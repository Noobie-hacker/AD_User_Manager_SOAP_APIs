package com.example.soap_crud.service;

import com.example.adservice.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.stereotype.Service;

import javax.naming.NamingException;
import javax.naming.directory.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class ActiveDirectoryService {

    @Autowired
    private LdapTemplate ldapTemplate;

    public List<User> getAllUsers() {
        String baseDn = "cn=Users,dc=mylab,dc=local"; // Add base DN for search scope
        return ldapTemplate.search(baseDn, "(objectClass=user)", new UserAttributesMapper());
    }

    public User getUserDetailsByCn(String cn) {
        String baseDn = "cn=Users,dc=mylab,dc=local";
        List<User> users = ldapTemplate.search(baseDn, "(cn=" + escapeXml(cn) + ")", new UserAttributesMapper());
        return users.isEmpty() ? null : users.get(0);
    }

    public User createUser(User user) {
        try {
            String userDn = "cn=" + escapeXml(user.getCn()) + ",cn=Users,dc=mylab,dc=local";
            DirContextAdapter context = new DirContextAdapter(userDn);

            // Set required attributes for AD user creation
            context.setAttributeValues("objectClass", new String[]{"top", "person", "organizationalPerson", "user"});
            context.setAttributeValue("cn", escapeXml(user.getCn()));
            context.setAttributeValue("sAMAccountName", escapeXml(user.getSamAccountName()));
            context.setAttributeValue("givenName", escapeXml(user.getFirstName()));
            context.setAttributeValue("sn", escapeXml(user.getLastName()));
            context.setAttributeValue("userPrincipalName", escapeXml(user.getUserPrincipalName()));
            context.setAttributeValue("mail", escapeXml(user.getEmail()));

            // Set the password (encoded as UTF-16LE for AD)
            String quotedPassword = "\"" + user.getPassword() + "\"";
            byte[] passwordBytes = quotedPassword.getBytes(StandardCharsets.UTF_16LE);
            context.setAttributeValue("unicodePwd", passwordBytes);

            // Enable the account (512 = NORMAL_ACCOUNT)
            context.setAttributeValue("userAccountControl", "512");

            // Bind the user to Active Directory
            ldapTemplate.bind(userDn, context, null);

            // Fetch the newly created user's details
            return getUserDetailsByCn(user.getCn());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to create user: " + e.getMessage(), e);
        }
    }


    public User updateUser(User user) {
        String userDn = "cn=" + escapeXml(user.getCn()) + ",cn=Users,dc=mylab,dc=local";

        List<ModificationItem> modsList = new ArrayList<>();

        if (user.getFirstName() != null) {
            modsList.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute("givenName", escapeXml(user.getFirstName()))));
        }
        if (user.getLastName() != null) {
            modsList.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute("sn", escapeXml(user.getLastName()))));
        }
        if (user.getEmail() != null) {
            modsList.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute("mail", escapeXml(user.getEmail()))));
        }

        if (!modsList.isEmpty()) {
            ModificationItem[] mods = modsList.toArray(new ModificationItem[0]);
            ldapTemplate.modifyAttributes(userDn, mods);
        } else {
            System.out.println("No attributes provided for update. Existing attributes remain unchanged.");
        }

        // Return the updated user
        return user;
    }



    public String deleteUserByCn(String cn) {
        try {
            ldapTemplate.unbind("cn=" + escapeXml(cn) + ",cn=Users,dc=mylab,dc=local");
            return "true";
        } catch (Exception e) {
            return "false";
        }
    }

    public String enableUserByCn(String cn) {
        return setUserAccountControl(cn, 512);
    }

    public String disableUserByCn(String cn) {
        return setUserAccountControl(cn, 514);
    }

    private String setUserAccountControl(String cn, int controlValue) {
        try {
            DirContextOperations context = ldapTemplate.lookupContext("cn=" + escapeXml(cn) + ",cn=Users,dc=mylab,dc=local");
            context.setAttributeValue("userAccountControl", String.valueOf(controlValue));
            ldapTemplate.modifyAttributes(context);
            return "true";
        } catch (Exception e) {
            return "false";
        }
    }

    public List<GroupInfo> getAllGroups() {
        String baseDn = "cn=Users,dc=mylab,dc=local";
        try {
            return ldapTemplate.search(baseDn, "(objectClass=group)", (AttributesMapper<GroupInfo>) attrs -> {
                String groupName = escapeXml((String) attrs.get("cn").get());
                String groupDn = escapeXml((String) attrs.get("distinguishedName").get());
                return new GroupInfo(groupName, groupDn);
            });
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public void addUserToGroups(String cn, List<String> groupDns) {
        String userDn = "cn=" + escapeXml(cn) + ",cn=Users,dc=mylab,dc=local";

        for (String groupDn : groupDns) {
            ldapTemplate.modifyAttributes(
                    escapeXml(groupDn),
                    new ModificationItem[]{
                            new ModificationItem(DirContext.ADD_ATTRIBUTE, new BasicAttribute("member", escapeXml(userDn)))
                    }
            );
        }
    }

    public void removeUserFromGroups(String cn, List<String> groupDns) {
        String userDn = "cn=" + escapeXml(cn) + ",cn=Users,dc=mylab,dc=local";

        for (String groupDn : groupDns) {
            ldapTemplate.modifyAttributes(
                    escapeXml(groupDn),
                    new ModificationItem[]{
                            new ModificationItem(DirContext.REMOVE_ATTRIBUTE, new BasicAttribute("member", escapeXml(userDn)))
                    }
            );
        }
    }

    private static class UserAttributesMapper implements AttributesMapper<User> {
        @Override
        public User mapFromAttributes(Attributes attrs) throws NamingException {
            User user = new User();

            user.setCn(getAttributeValue(attrs, "cn"));
            user.setSamAccountName(getAttributeValue(attrs, "sAMAccountName"));
            user.setDistinguishedName(getAttributeValue(attrs, "distinguishedName"));
            user.setUserPrincipalName(getAttributeValue(attrs, "userPrincipalName"));
            user.setUserAccountControl(getAttributeValue(attrs, "userAccountControl"));

            byte[] objectGUIDBytes = attrs.get("objectGUID") != null ? (byte[]) attrs.get("objectGUID").get() : null;
            user.setObjectGUID(convertObjectGUIDToUUID(objectGUIDBytes));

            user.setFirstName(getAttributeValue(attrs, "givenName"));
            user.setLastName(getAttributeValue(attrs, "sn"));
            user.setEmail(getAttributeValue(attrs, "mail"));

            if (attrs.get("memberOf") != null) {
                for (int i = 0; i < attrs.get("memberOf").size(); i++) {
                    user.getMemberOf().add(attrs.get("memberOf").get(i).toString());
                }
            }

            return user;
        }

        private String getAttributeValue(Attributes attrs, String attributeName) throws NamingException {
            return attrs.get(attributeName) != null ? (String) attrs.get(attributeName).get() : "";
        }

        private String convertObjectGUIDToUUID(byte[] objectGUID) {
            if (objectGUID == null || objectGUID.length != 16) {
                return null;
            }

            return String.format("%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
                    objectGUID[3], objectGUID[2], objectGUID[1], objectGUID[0],
                    objectGUID[5], objectGUID[4],
                    objectGUID[7], objectGUID[6],
                    objectGUID[8], objectGUID[9],
                    objectGUID[10], objectGUID[11], objectGUID[12], objectGUID[13], objectGUID[14], objectGUID[15]);
        }
    }

    private static String escapeXml(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
                .replace(",", "&comma;")
                .replace("\\", "&bsol;")
                .replace("#", "&#35;")
                .replace("+", "&#43;")
                .replace("=", "&#61;")
                .replace(";", "&#59;");
    }



    public static class GroupInfo {
        private String groupName;
        private String groupDn;

        public GroupInfo(String groupName, String groupDn) {
            this.groupName = groupName;
            this.groupDn = groupDn;
        }

        public String getGroupName() {
            return groupName;
        }

        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }

        public String getGroupDn() {
            return groupDn;
        }

        public void setGroupDn(String groupDn) {
            this.groupDn = groupDn;
        }
    }
}
