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
import javax.naming.ldap.LdapContext;
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
        String baseDn = "cn=Users,dc=mylab,dc=local"; // Add base DN for search scope
        List<User> users = ldapTemplate.search(baseDn, "(cn=" + cn + ")", new UserAttributesMapper());
        return users.isEmpty() ? null : users.get(0);
    }


    public String createUser(User user) {
        try {
            String userDn = "cn=" + user.getCn() + ",cn=Users,dc=mylab,dc=local";
            DirContextAdapter context = new DirContextAdapter(userDn);
            context.setAttributeValues("objectClass", new String[]{"top", "person", "organizationalPerson", "user"});
            context.setAttributeValue("cn", user.getCn());
            context.setAttributeValue("sAMAccountName", user.getSamAccountName());
            context.setAttributeValue("givenName", user.getFirstName());
            context.setAttributeValue("sn", user.getLastName());
            context.setAttributeValue("userPrincipalName", user.getUserPrincipalName());
            context.setAttributeValue("mail", user.getEmail());

            // Set the password
            String quotedPassword = "\"" + user.getPassword() + "\"";
            byte[] passwordBytes = quotedPassword.getBytes(StandardCharsets.UTF_16LE);
            context.setAttributeValue("unicodePwd", passwordBytes);

            // Set account control (normal account)
            context.setAttributeValue("userAccountControl", "512");

            ldapTemplate.bind(userDn, context, null);
            return "true"; // Return "true" on success
        } catch (Exception e) {
            e.printStackTrace();
            return "false"; // Return "false" on failure
        }
    }




    public User updateUser(User user) {
        String userDn = "cn=" + user.getCn() + ",cn=Users,dc=mylab,dc=local";

        List<ModificationItem> modsList = new ArrayList<>();

        // Add modifications only for non-null fields
        if (user.getFirstName() != null) {
            modsList.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute("givenName", user.getFirstName())));
        }
        if (user.getLastName() != null) {
            modsList.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute("sn", user.getLastName())));
        }
        if (user.getEmail() != null) {
            modsList.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute("mail", user.getEmail())));
        }

        // Check if there's any attribute to modify
        if (!modsList.isEmpty()) {
            ModificationItem[] mods = modsList.toArray(new ModificationItem[0]);
            ldapTemplate.modifyAttributes(userDn, mods);
        } else {
            System.out.println("No attributes provided for update. Existing attributes remain unchanged.");
        }

        // Return the updated user object
        return user;
    }






    public String deleteUserByCn(String cn) {
        try {
            ldapTemplate.unbind("cn=" + cn + ",cn=Users,dc=mylab,dc=local");
            return "true"; // Return "true" on success
        } catch (Exception e) {
            return "false"; // Return "false" on failure
        }
    }

    public String enableUserByCn(String cn) {
        return setUserAccountControl(cn, 512); // Enable user account
    }

    public String disableUserByCn(String cn) {
        return setUserAccountControl(cn, 514); // Disable user account
    }

    private String setUserAccountControl(String cn, int controlValue) {
        try {
            DirContextOperations context = ldapTemplate.lookupContext("cn=" + cn + ",cn=Users,dc=mylab,dc=local");
            context.setAttributeValue("userAccountControl", String.valueOf(controlValue));
            ldapTemplate.modifyAttributes(context);
            return "true"; // Ensure "true" is returned on success
        } catch (Exception e) {
            return "false"; // Ensure "false" is returned on failure
        }
    }

    public List<GroupInfo> getAllGroups() {
        String baseDn = "cn=Users,dc=mylab,dc=local"; // Add base DN for search scope
        try {
            return ldapTemplate.search(baseDn, "(objectClass=group)", (AttributesMapper<GroupInfo>) attrs -> {
                String groupName = (String) attrs.get("cn").get();
                String groupDn = (String) attrs.get("distinguishedName").get();
                return new GroupInfo(groupName, groupDn);
            });
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static class GroupInfo {
        private String groupName;
        private String groupDn;

        public GroupInfo(String groupName, String groupDn) {
            this.groupName = groupName;
            this.groupDn = groupDn;
        }

        // Getters and setters (if needed)

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




    public void addUserToGroups(String cn, List<String> groups) {
        String userDn = "cn=" + cn + ",cn=Users,dc=mylab,dc=local";

        for (String group : groups) {
            String groupDn = "cn=" + group + ",cn=Users,dc=mylab,dc=local";

            ldapTemplate.modifyAttributes(
                    groupDn,
                    new ModificationItem[]{
                            new ModificationItem(DirContext.ADD_ATTRIBUTE, new BasicAttribute("member", userDn))
                    }
            );
        }
    }

    public void removeUserFromGroups(String cn, List<String> groups) {
        String userDn = "cn=" + cn + ",cn=Users,dc=mylab,dc=local";

        for (String group : groups) {
            String groupDn = "cn=" + group + ",cn=Users,dc=mylab,dc=local";

            ldapTemplate.modifyAttributes(
                    groupDn,
                    new ModificationItem[]{
                            new ModificationItem(DirContext.REMOVE_ATTRIBUTE, new BasicAttribute("member", userDn))
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

            // Convert objectGUID to UUID
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
}
