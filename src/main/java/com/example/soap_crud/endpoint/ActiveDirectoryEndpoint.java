package com.example.soap_crud.endpoint;

import com.example.adservice.*;
import com.example.soap_crud.service.ActiveDirectoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import java.util.List;

@Endpoint
public class ActiveDirectoryEndpoint {

    private static final String NAMESPACE_URI = "http://example.com/adservice";

    @Autowired
    private ActiveDirectoryService adService;

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "getAllUsersRequest")
    @ResponsePayload
    public GetAllUsersResponse getAllUsers(@RequestPayload GetAllUsersRequest request) {
        GetAllUsersResponse response = new GetAllUsersResponse();
        List<User> users = adService.getAllUsers();
        response.getUsers().addAll(users);
        return response;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "getUserDetailsRequest")
    @ResponsePayload
    public GetUserDetailsResponse getUserDetails(@RequestPayload GetUserDetailsRequest request) {
        GetUserDetailsResponse response = new GetUserDetailsResponse();
        User user = adService.getUserDetailsByCn(request.getCn());
        response.setUser(user);
        return response;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "createUserRequest")
    @ResponsePayload
    public CreateUserResponse createUser(@RequestPayload CreateUserRequest request) {
        CreateUserResponse response = new CreateUserResponse();

        try {
            // Extract user details from request
            User requestUser = request.getUser();

            // Create a new User object to be passed to the service
            User user = new User();
            user.setCn(requestUser.getCn());
            user.setSamAccountName(requestUser.getSamAccountName());
            user.setDistinguishedName(requestUser.getDistinguishedName());
            user.setUserPrincipalName(requestUser.getUserPrincipalName());
            user.setObjectGUID(requestUser.getObjectGUID());
            user.setFirstName(requestUser.getFirstName());
            user.setLastName(requestUser.getLastName());
            user.setEmail(requestUser.getEmail());
            user.setPassword(requestUser.getPassword());
            //user.setMemberOf(requestUser.getMemberOf());
            user.setUserAccountControl(requestUser.getUserAccountControl());

            // Call the service to create the user in Active Directory
            User createdUser = adService.createUser(user);

            // Set the created user details in the response
            response.setCreatedUser(createdUser);

            // Set status message
            response.setStatus("User created successfully.");
        } catch (Exception e) {
            e.printStackTrace(); // Log the error for debugging
            response.setStatus("Failed to create user: " + e.getMessage());
        }

        return response;
    }



    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "updateUserRequest")
    @ResponsePayload
    public UpdateUserResponse updateUser(@RequestPayload UpdateUserRequest request) {
        UpdateUserResponse response = new UpdateUserResponse();
        UpdateUserResponse.UpdatedUser updatedUser = new UpdateUserResponse.UpdatedUser();

        try {
            // Create User object from request data
            User user = new User();
            user.setCn(request.getCn());
            user.setFirstName(request.getFirstName());
            user.setLastName(request.getLastName());
            user.setEmail(request.getEmail());

            // Call the service method to update the user
            User updated = adService.updateUser(user);

            // Populate the response's updatedUser field
            updatedUser.setCn(updated.getCn());
            updatedUser.setFirstName(updated.getFirstName());
            updatedUser.setLastName(updated.getLastName());
            updatedUser.setEmail(updated.getEmail());
            response.setUpdatedUser(updatedUser);

            // Set the status message
            response.setStatus("User updated successfully.");
        } catch (Exception e) {
            e.printStackTrace(); // Log the error for debugging
            response.setStatus("Failed to update user: " + e.getMessage());
        }

        return response;
    }








    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "deleteUserRequest")
    @ResponsePayload
    public DeleteUserResponse deleteUser(@RequestPayload DeleteUserRequest request) {
        DeleteUserResponse response = new DeleteUserResponse();
        boolean success = Boolean.parseBoolean(adService.deleteUserByCn(request.getCn()));
        response.setStatus(success ? "User deleted successfully." : "Failed to delete user.");
        return response;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "enableUserRequest")
    @ResponsePayload
    public EnableUserResponse enableUser(@RequestPayload EnableUserRequest request) {
        EnableUserResponse response = new EnableUserResponse();
        boolean success = Boolean.parseBoolean(adService.enableUserByCn(request.getCn()));
        response.setStatus(success ? "User enabled successfully." : "Failed to enable user.");
        return response;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "disableUserRequest")
    @ResponsePayload
    public DisableUserResponse disableUser(@RequestPayload DisableUserRequest request) {
        DisableUserResponse response = new DisableUserResponse();
        boolean success = Boolean.parseBoolean(adService.disableUserByCn(request.getCn()));
        response.setStatus(success ? "User disabled successfully." : "Failed to disable user.");
        return response;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "getAllGroupsRequest")
    @ResponsePayload
    public GetAllGroupsResponse getAllGroups(@RequestPayload GetAllGroupsRequest request) {
        GetAllGroupsResponse response = new GetAllGroupsResponse();
        List<ActiveDirectoryService.GroupInfo> groups = adService.getAllGroups();

        for (ActiveDirectoryService.GroupInfo group : groups) {
            GetAllGroupsResponse.Group groupElement = new GetAllGroupsResponse.Group();
            groupElement.setGroupName(group.getGroupName());
            groupElement.setGroupDn(group.getGroupDn());
            response.getGroup().add(groupElement);
        }

        return response;
    }



    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "addUserToGroupsRequest")
    @ResponsePayload
    public AddUserToGroupsResponse addUserToGroups(@RequestPayload AddUserToGroupsRequest request) {
        AddUserToGroupsResponse response = new AddUserToGroupsResponse();
        try {
            adService.addUserToGroups(request.getCn(), request.getGroups());
            response.setStatus("User added to groups successfully.");
        } catch (Exception e) {
            response.setStatus("Failed to add user to groups: " + e.getMessage());
        }
        System.out.println("Response Status: " + response.getStatus());

        return response;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "removeUserFromGroupsRequest")
    @ResponsePayload
    public RemoveUserFromGroupsResponse removeUserFromGroups(@RequestPayload RemoveUserFromGroupsRequest request) {
        RemoveUserFromGroupsResponse response = new RemoveUserFromGroupsResponse();
        try {
            adService.removeUserFromGroups(request.getCn(), request.getGroups());
            response.setStatus("User removed from groups successfully.");
        } catch (Exception e) {
            response.setStatus("Failed to remove user from groups: " + e.getMessage());
        }
        return response;
    }

}
