/*
 * Copyright 2006 Wyona
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.wyona.org/licenses/APACHE-LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.wyona.yanel.impl.resources;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.wyona.yanel.core.Resource;
import org.wyona.yanel.core.ResourceConfiguration;
import org.wyona.yanel.core.util.PathUtil;
import org.wyona.yanel.core.attributes.viewable.View;
import org.wyona.yanel.core.attributes.viewable.ViewDescriptor;
import org.wyona.yanel.core.api.attributes.ViewableV2;
import org.wyona.yanel.core.api.attributes.CreatableV2;

import org.wyona.security.core.api.UserManager;
import org.wyona.security.core.api.GroupManager;
import org.wyona.security.core.api.User;
import org.wyona.security.core.api.Group;
import org.wyona.security.core.api.AccessManagementException;
import org.wyona.yarep.core.Repository;
import org.apache.log4j.Category;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.servlet.http.HttpServletRequest;

/**
 * @deprecated Use EditYanelUserProfileResource instead.
 *
 * This resource allows creating, editing and deleting users. A user is defined
 * by the following parameters: userId userName (optional) group email password
 */
public class YanelUserResource extends Resource implements ViewableV2, CreatableV2 {

    protected static final String POLICY = "policy";

    protected static final String ROLE = "role";

    protected static final String WORLD = "world";

    protected static final String USER = "user";

    protected static final String GROUP = "group";

    protected static final String PERMISSION_ATTR = "permission";

    protected static final String ID_ATTR = "id";

    protected static final String XMLNS_ATTR = "xmlns";

    protected static final String XMLNS = "http://www.wyona.org/security/1.0";

    protected static final String MIME_TYPE = "application/xhtml+xml; charset=UTF-8";

    private static Category log = Category.getInstance(YanelUserResource.class);

    private HashMap<String, Object> properties = new HashMap<String, Object>();

    /**
     * Constructor
     */
    public YanelUserResource() {

        properties.put("userId", "");
        properties.put("userName", "");
        properties.put("email", "");
        properties.put("password", "");
        properties.put("passwordConfirmed", "");
        //properties.put("group", "");

    }

    /**
     * @see org.wyona.yanel.core.api.attributes.ViewableV2#getViewDescriptors()
     */
    public ViewDescriptor[] getViewDescriptors() {
        ViewDescriptor[] vd = new ViewDescriptor[2];
        vd[0] = new ViewDescriptor("default");
        vd[0].setMimeType(getMimeType(null));
        vd[1] = new ViewDescriptor("xml");
        vd[1].setMimeType(getMimeType("xml"));
        return vd;
    }

    /**
     * @see org.wyona.yanel.core.api.attributes.ViewableV2#getMimeType(java.lang.String)
     */
    public String getMimeType(String viewId) {
        if (viewId != null) {
            if (viewId.equals("xml")) return "application/xml";
        }
        return MIME_TYPE;
    }

    /**
     * @see org.wyona.yanel.core.api.attributes.ViewableV2#getView(String)
     * @return The requested view. Possible views are:
     * defaultView - Displays change password, edit profile and delete forms
     * submitProfile - Result of updating the user's profile
     * submitPassword - Result of changing the user's password
     * submitDelete - Result of deleting the user
     */
    public View getView(String viewId) throws Exception {
        View defaultView = new View();
        try {

            File xslFile = org.wyona.commons.io.FileUtil.file(rtd.getConfigFile().getParentFile().getAbsolutePath(), "xslt" + File.separator + "yanel-user-profile.xsl");
            Transformer transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(xslFile));

            String action = determineAction(request);
            String userId = getUserId();
            if (userId == null) {
                log.error("No user ID!");
                throw new Exception("No user ID!");
            }

            if (action.equals("submitProfile")) {
                updateUserProfile(request, transformer);
            } else if (action.equals("submitPassword")) {
                updatePassword(request, transformer);
            } else if (action.equals("submitDelete")) {
                if (deleteUser(userId)) {
                    transformer.setParameter("success", "User deleted successfully");
                } else {
                    transformer.setParameter("error", "Unable to delete user successfully");
                }
                transformer.setParameter("userId", userId);
                transformer.setParameter("deletion", "true");
            } else if (action.startsWith("submitDeleteFromGroup")) {
                deleteFromGroup(action, transformer);
            } else if (action.equals("submitAddToGroup")) {
                addToGroup(request,transformer);
            }

            if(!action.equals("submitDelete")) {
                User user = getRealm().getIdentityManager().getUserManager().getUser(userId);
                if (user == null) {
                    log.error("No such user: " + userId);
                    throw new Exception("No such user: " + userId);
                }

                transformer.setParameter("userId", userId);
                transformer.setParameter("userName", user.getName());
                transformer.setParameter("email", user.getEmail());
                if (user.getDescription() != null) {
                    transformer.setParameter("description", user.getDescription());
                }
                if (user.getLanguage() != null) {
                    transformer.setParameter("user-profile-language", user.getLanguage());
                }
                if (user.getExpirationDate() != null) {
                    transformer.setParameter("expiration-date", user.getExpirationDate());
                }
                Group[] userGroups = user.getGroups();
                StringBuffer userGroupsString = new StringBuffer();
                for (int i = 0; i < userGroups.length; i++) {
                    userGroupsString.append(userGroups[i].getID()).append(";");
                }
                transformer.setParameter("userGroupsString", userGroupsString);

                Group[] allGroups = getRealm().getIdentityManager().getGroupManager().getGroups();
                StringBuffer allGroupsString = new StringBuffer();
                for (int i = 0; i < allGroups.length; i++) {
                    boolean isMember = false;
                    for(int j = 0; j < userGroups.length; j++) {
                        if(userGroups[j].getID().equals(allGroups[i].getID())) {
                            isMember = true;
                        }
                    }
                    if(!isMember) {
                        allGroupsString.append(allGroups[i].getID()).append(";");
                    }
                }
                transformer.setParameter("allGroupsString", allGroupsString);
            }
            defaultView.setMimeType(getMimeType(viewId));
            if (viewId != null && viewId.equals("xml")) {
                defaultView.setInputStream(getXMLAsStream());
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                String backToRealm = org.wyona.yanel.core.util.PathUtil.backToRealm(getPath());
                String reservedPrefix = yanel.getReservedPrefix();
                transformer.setParameter("yanel.back2realm", backToRealm);
                transformer.setParameter("yanel.reservedPrefix", reservedPrefix);
                transformer.transform(new javax.xml.transform.stream.StreamSource(getXMLAsStream()), new StreamResult(baos));
                defaultView.setInputStream(new java.io.ByteArrayInputStream(baos.toByteArray()));
            }
        } catch (Exception e) {
            // TODO: Improve exception handling
            log.error(e.getMessage(), e);
        }

        return defaultView;
    }

    /**
     * @see org.wyona.yanel.core.api.attributes.ViewableV2#exists()
     */
    public boolean exists() throws Exception {
        String userId = getUserId();
        if (userId != null) {
            if (realm.getIdentityManager().getUserManager().getUser(userId) != null) {
                return true;
            } else {
                log.error("No such user: " + userId);
            }
        } else {
            log.error("No user ID!");
        }
        return false;
    }

    /**
     * @see org.wyona.yanel.core.api.attributes.ViewableV2#getSize()
     */
    public long getSize() throws Exception {
        log.warn("Not implemented yet!");
        return -1;
    }

    /**
     * @see org.wyona.yanel.core.api.attributes.CreatableV2#getPropertyType(String)
     */
    public String getPropertyType(String propertyName) {
        if (propertyName.equals("password") || propertyName.equals("passwordConfirmed")) {
            return CreatableV2.TYPE_PASSWORD;
        }
        return null;
    }

    /**
     * @see org.wyona.yanel.core.api.attributes.CreatableV2#create(HttpServletRequest)
     */
    public void create(HttpServletRequest request) {
        try {
            if (!getRealm().getIdentityManager().getUserManager().existsUser("rp.userId")) {
                boolean doCreate = validateNewUserData(request);
                if (doCreate) {
                    UserManager um = getRealm().getIdentityManager().getUserManager();
                    String userId = request.getParameter("rp.userId");
                    String password = request.getParameter("rp.password");
                    String email = request.getParameter("rp.email");
                    String userName = request.getParameter("rp.userName");
                    //String userProfileLanguage = request.getParameter("rp.userProfileLanguage");

                    um.createUser(userId, userName, email, password);

                    // TODO: Handle more than one group
                    String groupId = request.getParameter("rp.group");
                    if (groupId != null) {
                        GroupManager gm = getRealm().getIdentityManager().getGroupManager();
                        Group group =  gm.getGroup(groupId);
                        group.addMember(um.getUser(userId));
                        group.save();
                    }

                    //saveUserPolicy(userId);
                } else {
                    log.error("Unable to create user: the data introduced was not valid");
                }
            } else {
                log.error("Unable to create user: the supplied id already exists");
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

    }

    /**
     * @see org.wyona.yanel.core.api.attributes.CreatableV2#createRTIProperties(HttpServletRequest)
     */
    public HashMap createRTIProperties(HttpServletRequest request) {
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("user", request.getParameter("rp.userId"));
        return map;
    }

    /**
     * If the suggested name is an empty string, then return null such that the resource creator will not create a resource configuration
     */
    public String getCreateName(String suggestedName) {
        if (suggestedName != null && suggestedName.equals("")) {
            return null;
        }
        return suggestedName;
    }

    /**
     * @see org.wyona.yanel.core.api.attributes.CreatableV1#getPropertyNames()
     */
    public String[] getPropertyNames() {
        String[] propertyNames = (String[]) properties.keySet().toArray(
                new String[properties.keySet().size()]);
        return propertyNames;
    }

    /**
     * @see org.wyona.yanel.core.api.attributes.CreatableV1#setProperty(String,
     *      Object)
     */
    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    /**
     * @see org.wyona.yanel.core.api.attributes.CreatableV1#getProperty(String)
     */
    public Object getProperty(String name) {
        Object property = properties.get(name);
        return property;
    }

//    /**
//     * Create a configuration object with the policies to access the user data.
//     * Only the user and lenya will be able to access the user data.
//     *
//     * @param userId
//     *            The user who must be granted permission to modify data
//     * @return Configuration object containing the corresponding policies
//     */
//    private Configuration createPolicyConfiguration(String userId) {
//        DefaultConfiguration config = new DefaultConfiguration(POLICY);
//        config.setAttribute(XMLNS_ATTR, XMLNS);
//
//        DefaultConfiguration child = null;
//
//        child = new DefaultConfiguration(ROLE);
//        child.setAttribute(ID_ATTR, "view");
//        config.addChild(child);
//
//        DefaultConfiguration worldNode = new DefaultConfiguration(WORLD);
//        worldNode.setAttribute(PERMISSION_ATTR, "false");
//        child.addChild(worldNode);
//
//        DefaultConfiguration userNode = new DefaultConfiguration(GROUP);
//        userNode.setAttribute(ID_ATTR, "admin");
//        userNode.setAttribute(PERMISSION_ATTR, "true");
//        child.addChild(userNode);
//
//        userNode = new DefaultConfiguration(USER);
//        userNode.setAttribute(ID_ATTR, userId);
//        userNode.setAttribute(PERMISSION_ATTR, "true");
//        child.addChild(userNode);
//
//        child = new DefaultConfiguration(ROLE);
//        child.setAttribute(ID_ATTR, "open");
//        config.addChild(child);
//
//        userNode = new DefaultConfiguration(GROUP);
//        userNode.setAttribute(ID_ATTR, "admin");
//        userNode.setAttribute(PERMISSION_ATTR, "true");
//        child.addChild(userNode);
//
//        userNode = new DefaultConfiguration(USER);
//        userNode.setAttribute(ID_ATTR, userId);
//        userNode.setAttribute(PERMISSION_ATTR, "true");
//        child.addChild(userNode);
//
//        child = new DefaultConfiguration(ROLE);
//        child.setAttribute(ID_ATTR, "write");
//        config.addChild(child);
//
//        userNode = new DefaultConfiguration(GROUP);
//        userNode.setAttribute(ID_ATTR, "admin");
//        userNode.setAttribute(PERMISSION_ATTR, "true");
//        child.addChild(userNode);
//
//        userNode = new DefaultConfiguration(USER);
//        userNode.setAttribute(ID_ATTR, userId);
//        userNode.setAttribute(PERMISSION_ATTR, "true");
//        child.addChild(userNode);
//
//        return config;
//    }

    /**
     * Updates the user profile
     *
     * @param request The request containing the data to update
     * @param transformer
     */
    private void updateUserProfile(HttpServletRequest request, Transformer transformer) {
        String email = request.getParameter("email");
        if (email == null || ("").equals(email)) {
            transformer.setParameter("error", "emailNotSet");
        } else if (!validateEmail(email)) {
            transformer.setParameter("error", "emailNotValid");
        } else {
            try {
                String userId = getUserId();
                User user = realm.getIdentityManager().getUserManager().getUser(userId);
                user.setEmail(request.getParameter("email"));
                user.setName(request.getParameter("userName"));
                user.setLanguage(request.getParameter("user-profile-language"));
                user.save();
                transformer.setParameter("success", "Profile updated successfully");
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Updates the groups the user belongs to. Currently it only allows for
     * deletion of membership The action parameter is always
     * submitDeleteFromGroup_X where X is the target group
     *
     * @param request
     *            The request containing the group involved
     * @param transformer
     */
    private void deleteFromGroup(String action, Transformer transformer) {
        try {
            String userId = getUserId();
            User user = getRealm().getIdentityManager().getUserManager().getUser(userId);
            Group[] userGroups = user.getGroups();
            GroupManager gm = getRealm().getIdentityManager().getGroupManager();

            String targetGroup = action.substring(action.indexOf("_")+1);
            if (userGroups.length > 0) {
                if(userGroups.length > 1) {
                    Group group = gm.getGroup(targetGroup);
                    if (group.isMember(user)) {
                        group.removeMember(user);
                        group.save();
                    }
                    transformer.setParameter("success", "User successfully deleted from group: " + targetGroup);
                } else {
                    transformer.setParameter("error", "User can not be removed from group: " + targetGroup + ". Users must belong to one group at least.");
                }
            } else {
                log.error("The user " + userId + "does not belong to any group!");
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * Add a user to a group
     * @param request Request containing the group the user has to be added to
     * @param transformer
     */
    private void addToGroup(HttpServletRequest request, Transformer transformer) {
        String groupId = request.getParameter("Group");
        try {
            String userId = getUserId();
            Group group = getRealm().getIdentityManager().getGroupManager().getGroup(groupId);
            User user = getRealm().getIdentityManager().getUserManager().getUser(userId);
            group.addMember(user);
            group.save();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        transformer.setParameter("success", "User successfully added to group: " + groupId);
    }

    /**
     * Change user password
     *
     * @param request
     * @param transformer
     */
    private void updatePassword(HttpServletRequest request, Transformer transformer) {
        String oldPassword = request.getParameter("oldPassword");
        try {
            String userId = getUserId();
            if (getRealm().getIdentityManager().getUserManager().getUser(userId).authenticate(
                    oldPassword)) {
                String plainPassword = request.getParameter("newPassword");
                boolean confirmation = plainPassword.equals(request
                        .getParameter("newPasswordConfirmation"));
                if (confirmation && !plainPassword.equals("")) {
                    User user = getRealm().getIdentityManager().getUserManager().getUser(userId);
                    user.setPassword(plainPassword);
                    user.save();

                    transformer.setParameter("success", "Password updated successfully");
                } else {
                    transformer.setParameter("error", "Either no new password was supplied "
                            + "or the password supplied and its confirmation do not coincide");
                }
            } else {
                transformer.setParameter("error", "Authentication failed!");
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * This method checks if the specified email is valid against a regex
     *
     * @param email
     * @return true if email is valid
     */
    private boolean validateEmail(String email) {
        String emailRegEx = "(\\w+)@(\\w+\\.)(\\w+)(\\.\\w+)*";
        Pattern pattern = Pattern.compile(emailRegEx);
        Matcher matcher = pattern.matcher(email);

        return matcher.find();
    }

    /**
     * Determine the requested view: defaultView, submitProfile,
     * submitPassword,submitGroup, submitDelete
     *
     * @param request
     * @return name of the desired view
     */
    private String determineAction(HttpServletRequest request) {
        boolean submit = false;
        String action = "defaultView";

        Enumeration<?> enumeration = request.getParameterNames();
        while (enumeration.hasMoreElements() && !submit) {
            action = enumeration.nextElement().toString();
            if (action.startsWith("submit")) {
                submit = true;
            }
        }
        return action;
    }

    /**
     * Validate the supplied user data. userName is optional
     */
    private boolean validateNewUserData(HttpServletRequest request)
    throws AccessManagementException {
        boolean isValid = true;
        if (request.getParameter("rp.password").equals("")) {
            isValid = false;
            log.warn("No password supplied");
        }
        if (request.getParameter("rp.passwordConfirmed").equals("")) {
            isValid = false;
            log.warn("No password confirmation supplied");
        }
        if (!request.getParameter("rp.passwordConfirmed").equals(request.getParameter("rp.password"))) {
            isValid = false;
            log.warn("Password and confirmed password do no match");
        }
        if (request.getParameter("rp.userId").equals("")) {
            isValid = false;
            log.warn("No userId supplied");
        }

/*
        String group = request.getParameter("rp.group");
        if (group.equals("") || !realm.getIdentityManager().getGroupManager().existsGroup(group)) {
            isValid = false;
            log.error("Either no group was supplied or the group does not exist");
        }
*/
        if (!validateEmail(request.getParameter("rp.email"))) {
            isValid = false;
            log.warn("No email supplied or email not valid");
        }
        return isValid;
    }

    /**
     * Get user id from resource configuration
     */
    private String getUserId() throws Exception {
        String userId = null;

        if (getEnvironment().getRequest().getParameter("id") != null) {
            return getEnvironment().getRequest().getParameter("id");
        }

        ResourceConfiguration resConfig = getConfiguration();
        if(resConfig != null) {
            userId = getConfiguration().getProperty("user");
        } else {
            log.warn("DEPRECATED: Do not use RTI but rather a resource configuration");
            userId = getRTI().getProperty("user");
        }
        return userId;
    }

    /**
     * Delete the user from the identities repository and remove the related
     * user interface files
     */
    private boolean deleteUser(String userId) {
        boolean success = false;
        try {

            UserManager um = getRealm().getIdentityManager().getUserManager();
            um.removeUser(userId);

            getRealm().getPolicyManager().removePolicy(getPath());

            Repository rtiRepo = getRealm().getRTIRepository();
            String userScreenRCPath = PathUtil.getRCPath(getPath());
            if(rtiRepo.existsNode(userScreenRCPath)) {
                rtiRepo.getNode(userScreenRCPath).delete();
            } else {
                String userScreenRTIPath = PathUtil.getRTIPath(getPath());
                rtiRepo.getNode(userScreenRTIPath).delete();
            }
            success = true;

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return success;
    }

    /**
     * Get XML as stream
     */
    private java.io.InputStream getXMLAsStream() throws Exception {
        String userId = getUserId();
        if (userId != null) {
            User user = realm.getIdentityManager().getUserManager().getUser(userId);
            StringBuilder sb = new StringBuilder();

            sb.append("<?xml version=\"1.0\"?>");
            sb.append("<user id=\"" + userId + "\" email=\"" + user.getEmail() + "\">");
            sb.append("</user>");
            //sb.append("<form name=\"user-profile-form\"/>");

            return new java.io.StringBufferInputStream(sb.toString());
        } else {
            return new java.io.StringBufferInputStream("<no-user-id/>");
        }


/*
        File xmlFile = org.wyona.commons.io.FileUtil.file(rtd.getConfigFile().getParentFile().getAbsolutePath(), "xml" + File.separator + "yanel-user-profile.xml");
        return new java.io.FileInputStream(xmlFile);
*/
    }
}
