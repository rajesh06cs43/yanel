/*
 * Copyright 2007 - 2016 Wyona
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

package org.wyona.yanel.impl.resources.collection;

import org.wyona.yanel.core.Environment;
import org.wyona.yanel.core.ResourceConfiguration;
import org.wyona.yanel.core.api.attributes.CreatableV2;
import org.wyona.yanel.core.api.attributes.ViewableV2;
import org.wyona.yanel.core.attributes.viewable.View;
import org.wyona.yanel.impl.resources.BasicXMLResource;

import javax.servlet.http.HttpServletRequest;

import org.wyona.yarep.core.Repository;
import org.wyona.yarep.core.Node;
import org.wyona.yanel.core.util.DateUtil;
import org.wyona.yanel.core.util.PathUtil;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.File;
import java.io.InputStream;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;

import java.util.Calendar;

/**
 * Resource in order to list nodes of a collection/directory
 */
public class CollectionResource extends BasicXMLResource implements ViewableV2, CreatableV2 {

    private static Logger log = LogManager.getLogger(CollectionResource.class);

    /**
     * @see org.wyona.yanel.core.api.attributes.ViewableV2#getView(java.lang.String)
     */
    public View getView(String viewId) throws Exception {
        return getXMLView(viewId, getContentXML());
    }

    /**
     * Get collection (directory listing) as XML
     */
    private InputStream getContentXML() throws Exception {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?>");
        try {
            String yanelPath = getCollectionPath();
            if (yanelPath.startsWith("file:")) {
                log.info("List children of actual file system directory '" + yanelPath + "' ...");
                sb.append(getContentXMLOfFileSystemDirectory(yanelPath.substring(5)));
            } else {
                sb.append(getContentXMLOfYarepNode(yanelPath));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return new java.io.StringBufferInputStream(sb.toString());
    }

    /**
     *
     */
    private String getCollectionPath() throws Exception {
        String yanelPath = getResourceConfigProperty("yanel-path");
        if (yanelPath != null) {
            return yanelPath;
        } else {
            return getPath();
        }
    }

    /**
     * Get file system directory listing as XML
     * @param path File system path (without 'file:' scheme/protocol)
     */
    private StringBuilder getContentXMLOfFileSystemDirectory(String path) throws Exception {
        log.debug("Selected path: " + path);

        if (new File(path).isFile()) {
            log.warn("Path '" + path + "' is a File instead a Directory, hence use parent of path!");
            path = new File(path).getParent();
        }

        if (!new File(path).isDirectory()) {
            log.warn("Path '" + path + "' is not a Directory!");
            return new StringBuilder("<no-such-directory>" + path + "</no-such-directory>");
        }

        // NOTE: The schema is according to
        // http://cocoon.apache.org/2.1/userdocs/directory-generator.html
        StringBuilder sb = new StringBuilder();
        sb.append("<dir:directory yanel:path=\"" + getPath() + "\" dir:name=\"" + new File(path).getName() + "\" dir:path=\"" + path + "\" xmlns:dir=\"http://apache.org/cocoon/directory/2.0\" xmlns:yanel=\"http://www.wyona.org/yanel/resource/directory/1.0\">");

        // TODO: Make ordering/sorting configurable!
        log.warn("TODO: Make order/sorting configurable!");
        log.warn("DEBUG: Path: " + path);
        File[] children = new File(path).listFiles();
        Calendar calendar = Calendar.getInstance();
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                if (children[i].isFile()) {
                    calendar.setTimeInMillis(children[i].lastModified());
                    String lastModified = DateUtil.format(calendar.getTime());
                    log.warn("DEBUG: File last modified: " + lastModified);
                    if (getResourceConfigProperty("date-format") != null) {
                        java.text.DateFormat df = new java.text.SimpleDateFormat(getResourceConfigProperty("date-format"));
                        lastModified = df.format(children[i].lastModified());
                        log.warn("DEBUG: File last modified (formatted): " + lastModified);
                    }
                    sb.append("<dir:file path=\"" + children[i].getPath() + "\" name=\"" + children[i].getName() + "\" lastModified=\"" + children[i].lastModified() + "\" date=\"" + lastModified + "\" size=\"" + children[i].length() + "\"/>");
                } else if (children[i].isDirectory()) {
                    calendar.setTimeInMillis(children[i].lastModified());
                    String lastModified = DateUtil.format(calendar.getTime());
                    sb.append("<dir:directory path=\"" + children[i].getPath() + "\" name=\"" + children[i].getName() + "\" lastModified=\"" + children[i].lastModified() + "\" date=\"" + lastModified + "\"/>");
                } else {
                    sb.append("<yanel:exception yanel:path=\"" + children[i] + "\"/>");
                }
            }
            if (children.length < 1) {
                sb.append("<yanel:no-children/>");
            }
        } else {
            sb.append("<yanel:no-children/>");
        }
        sb.append("</dir:directory>");

        return sb;
    }

    /**
     * Get yarep collection listing as XML
     * @param path Path of directory/collection
     * @return listing of child nodes of directory/collection as XML
     */
    private StringBuilder getContentXMLOfYarepNode(String path) throws Exception {
        Repository repo = getRealm().getRepository();
        log.debug("Selected path: " + path);

        if (!repo.existsNode(path)) {
            log.warn("No such node '" + path + "'!");
            StringBuilder sb = new StringBuilder();
            sb.append("<dir:directory yanel:repository-configuration-file=\"" + repo.getConfigFile() + "\" yanel:path=\"" + getPath() + "\" dir:name=\"" + "TODO" + "\" dir:path=\"" + path + "\" xmlns:dir=\"http://apache.org/cocoon/directory/2.0\" xmlns:yanel=\"http://www.wyona.org/yanel/resource/directory/1.0\">");
            sb.append("<yanel:exception>No such node: " + path + "</yanel:exception>");
            sb.append("</dir:directory>");
            return sb;
        }

        // TODO: This doesn't seem to work ... (check on Yarep ...)
        if (repo.getNode(path).isResource()) {
            log.warn("Path is a resource instead of a collection: " + path);
            // p = p.getParent();
        }
        // TODO: Implement org.wyona.yarep.core.Path.getParent()
        if (!repo.getNode(path).isCollection()) {
            log.warn("Path is not a collection: " + path);
            log.warn("Use parent of path: " + repo.getNode(path).getParent().getPath());
        }
        // TODO: Add realm prefix, e.g. realm-prefix="ulysses-demo"
        // NOTE: The schema is according to
        // http://cocoon.apache.org/2.1/userdocs/directory-generator.html
        StringBuilder sb = new StringBuilder();
        sb.append("<dir:directory yanel:repository-configuration-file=\"" + repo.getConfigFile() + "\" yanel:path=\"" + getPath() + "\" dir:name=\"" + repo.getNode(path).getName() + "\" dir:path=\"" + path + "\" xmlns:dir=\"http://apache.org/cocoon/directory/2.0\" xmlns:yanel=\"http://www.wyona.org/yanel/resource/directory/1.0\">");
        // TODO: Do not show the children with suffix .yanel-rti resp. make this configurable!
        // NOTE: Do not hardcode the .yanel-rti, but rather use Path.getRTIPath ...
        Node[] children = repo.getNode(path).getNodes();
        Calendar calendar = Calendar.getInstance();
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                if (children[i].isResource()) {
                    calendar.setTimeInMillis(children[i].getLastModified());
                    String lastModified = DateUtil.format(calendar.getTime());
                    log.debug("File last modified: " + lastModified);
                    if (getResourceConfigProperty("date-format") != null) {
                        java.text.DateFormat df = new java.text.SimpleDateFormat(getResourceConfigProperty("date-format"));
                        lastModified = df.format(children[i].getLastModified());
                        log.debug("File last modified (formatted): " + lastModified);
                    }
                    sb.append("<dir:file");
                    sb.append(" path=\"" + children[i].getPath() + "\" name=\"" + children[i].getName() + "\" lastModified=\"" + children[i].getLastModified() + "\" date=\"" + lastModified + "\" size=\"" + children[i].getSize() + "\"");
                    String workflowState = getWorkflowState(children[i]);
                    if (workflowState != null) {
                        sb.append(" workflow-state=\"" + workflowState + "\"");
                    } else {
                        log.debug("Node '" + children[i].getPath() + "' has no workflow state set.");
                    }
                    sb.append("/>");
                } else if (children[i].isCollection()) {
                    sb.append("<dir:directory path=\"" + children[i].getPath() + "\" name=\"" + children[i].getName() + "\"/>");
                } else {
                    sb.append("<yanel:exception yanel:path=\"" + children[i] + "\"/>");
                }
            }
            if (children.length < 1) {
                sb.append("<yanel:no-children/>");
            }
        } else {
            sb.append("<yanel:no-children/>");
        }
        sb.append("</dir:directory>");

        return sb;
    }

    /**
     * Get workflow state if available
     * @param node Node which might has a workflow state
     * @return workflow state associated with node if available and null otherwise
     */
    private String getWorkflowState(Node node) throws Exception {
        if (org.wyona.commons.clazz.ClazzUtil.implementsInterface(node, "org.wyona.yarep.core.attributes.VersionableV1")) {
            java.util.Iterator<org.wyona.yarep.core.Revision> revisions = ((org.wyona.yarep.core.attributes.VersionableV1) node).getRevisions(false);
            if (revisions != null && revisions.hasNext()) {
                String revisionName = ((org.wyona.yarep.core.Revision) revisions.next()).getRevisionName();
                return org.wyona.yanel.core.workflow.WorkflowHelper.getWorkflowState(node, revisionName);
            } else {
                log.warn("Node '" + node.getPath() + "' has no revisions.");
                return null;
            }
        } else {
            log.warn("Node implementation is not VersionableV1");
            return null;
        }
    }

    /**
     * @see org.wyona.yanel.impl.resources.BasicXMLResource#getXMLView(String, InputStream)
     */
    @Override
    public View getXMLView(String viewId, InputStream xmlInputStream) throws Exception {
        if (isDefaultXSLTDisabled()) {
            return super.getXMLView(viewId, xmlInputStream);
        }

        log.warn("DEBUG: ViewId: " + viewId);
        if (viewId == null || !viewId.equals("source")) {
            TransformerFactory tfactory = TransformerFactory.newInstance();
            Transformer transformerIntern = tfactory.newTransformer(getXSLTStreamSource());

            transformerIntern.setParameter("yanel.path.name", org.wyona.commons.io.PathUtil.getName(getPath()));
            transformerIntern.setParameter("yanel.path", getPath().toString());
            transformerIntern.setParameter("yanel.back2context", backToContext()+backToRoot());
            transformerIntern.setParameter("yarep.back2realm", backToRoot());
            transformerIntern.setParameter("yarep.parent", getParent(getPath()));

            transformerIntern.setParameter("yanel.globalHtdocsPath", PathUtil.getGlobalHtdocsPath(this));
            // DEPREACTED: transformerIntern.setParameter("yanel.htdocs", PathUtil.getGlobalHtdocsPath(this));

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            transformerIntern.transform(new StreamSource(xmlInputStream), new StreamResult(baos));

            return super.getXMLView(viewId, new java.io.ByteArrayInputStream(baos.toByteArray()));
        }
        return super.getXMLView(viewId, xmlInputStream);
    }

    /**
     * Check whether default XSLT is disabled
     * @return true when default XSLT is disabled and false otherwise
     */
    private boolean isDefaultXSLTDisabled() throws Exception {
        String disabledStr = getResourceConfigProperty("default-xslt_disabled");
        if (disabledStr != null && disabledStr.equals("true")) {
            return true;
        }
        return false;
    }

    /**
     * Gets the names of the i18n message catalogues used for the i18n transformation.
     * Looks for an rc config property named 'i18n-catalogue'. Defaults to 'global'.
     * @return i18n catalogue name
     */
    protected String[] getI18NCatalogueNames() throws Exception {
        String[] cataloguesOfSuperClass = super.getI18NCatalogueNames();
        java.util.ArrayList<String> catalogues = new java.util.ArrayList<String>();
        for (int i = 0; i < cataloguesOfSuperClass.length; i++) {
            catalogues.add(cataloguesOfSuperClass[i]);
        }
        catalogues.add("directory");
        return catalogues.toArray(new String[catalogues.size()]);
/*
        String[] array = {"global","directory"};
        return array;
*/
    }

    /**
     * @see org.wyona.yanel.core.api.attributes.ViewableV2#exists()
     */
    public boolean exists() throws Exception {
        String yanelPath = getCollectionPath();
        if (yanelPath.startsWith("file:") && new File(yanelPath.substring(5)).isDirectory()) {
            return true;
        }
        if (getRealm().getRepository().existsNode(yanelPath)) {
            return true;
        }
        log.warn("No such collection '" + yanelPath + "'!");
        return false;
    }

    /**
     *
     */
    public long getSize() throws Exception {
        // TODO: not implemented yet
        log.warn("TODO: Method is not implemented yet");
        return -1;
    }

    /**
     *
     */
    private StreamSource getXSLTStreamSource() throws Exception {
        String customDefaultXSLT = getResourceConfigProperty("default-xslt");
        if (customDefaultXSLT != null) {
            return new StreamSource(getRealm().getRepository().getNode(customDefaultXSLT).getInputStream());
        }

        // INFO: If no property 'default-xslt' set, then fallback to XSLT part of this resource
        File defaultXSLTFile = org.wyona.commons.io.FileUtil.file(rtd.getConfigFile().getParentFile().getAbsolutePath(), "htdocs" + File.separator + "default_dir2xhtml.xsl");
        if (log.isDebugEnabled()) log.debug("XSLT file: " + defaultXSLTFile);
        return new StreamSource(defaultXSLTFile);
    }

    /**
     * @see org.wyona.yanel.impl.resources.BasicXMLResource#getMimeType(String)
     */
    @Override
    public String getMimeType(String viewId) throws Exception {
        String mimeType = null;

        ResourceConfiguration rc = getConfiguration();
        if (rc != null) {
            mimeType = rc.getProperty("mime-type");
        } else {
            mimeType = getRTI().getProperty("mime-type");
        }
        if (mimeType != null) return mimeType;

        mimeType = super.getMimeType(viewId);
        if (mimeType != null) return mimeType;

        // NOTE: Assuming fallback re default_dir2xhtml.xsl ...
        mimeType = "application/xhtml+xml";
        log.warn("No content type configured, hence fallback to '" + mimeType + "' ...");
        return mimeType;
    }

    /**
     * @return a String with as many ../ as it needs to go back to from current realm to context
     */
    private String backToContext() {
        String backToContext = "";
        int steps = realm.getMountPoint().split("/").length - 1;
        for (int i = 0; i < steps; i++) {
            backToContext = backToContext + "../";
        }
        return backToContext;
    }

    /**
     * @return a String with as many ../ as it needs to go back to from current resource to the realm-root
     */
    private String backToRoot() {
        String backToRoot = "";
        int steps;

        // TODO: Wouldn't it make more sense to use "tokens" and use a URL rewriter at the very end (also see the portlet specificatio http://jcp.org/aboutJava/communityprocess/review/jsr168/)
        String resourceContainerPath = getEnvironment().getResourceContainerPath();
        if (log.isDebugEnabled()) {
            log.debug("Resource container path: " + resourceContainerPath);
            log.debug("Resource path: " + getPath());
        }
        if (resourceContainerPath != null) {
            if (resourceContainerPath.endsWith("/") && !resourceContainerPath.equals("/")) {
                steps = resourceContainerPath.split("/").length - 1;
            } else {
                steps = resourceContainerPath.split("/").length - 2;
            }
        } else {
            if (getPath().endsWith("/") && !getPath().equals("/")) {
                steps = getPath().split("/").length - 1;
            } else {
                steps = getPath().split("/").length - 2;
            }
        }

        for (int i = 0; i < steps; i++) {
            backToRoot = backToRoot + "../";
        }
        return backToRoot;
    }

    /**
     * @return a String ../ if path ends with a trailing slash. Otherwise a String ./
     */
    private String getParent(String path) {
        String parentPath = "./";
        if (path.endsWith("/")) {
            parentPath = "../";
        }
        return parentPath;
    }

    /**
     * @see org.wyona.yanel.core.api.attributes.CreatableV2#create(HttpServletRequest)
     */
   public void create(HttpServletRequest request) {
       try {
           Repository repo = getRealm().getRepository();
           org.wyona.yanel.core.util.YarepUtil.addNodes(repo, getPath().toString(), org.wyona.yarep.core.NodeType.COLLECTION);
       } catch (Exception e) {
           log.error(e.getMessage(), e);
       }
   }

   /**
    *
    */
   public java.util.HashMap createRTIProperties(HttpServletRequest request) {
       java.util.HashMap map = new java.util.HashMap();
       map.put("xslt", request.getParameter("rp.xslt"));
       map.put("mime-type", request.getParameter("rp.mime-type"));
       return map;
   }

   public String getCreateName(String suggestedName) {
       return suggestedName;
   }

   /**
    *
    */
   public String getPropertyType(String name) {
       log.warn("Not implemented yet!");
       return null;
   }

   /**
    *
    */
   public Object getProperty(String name) {
       log.warn("Not implemented yet!");
       return null;
   }

   /**
    *
    */
   public String[] getPropertyNames() {
       log.warn("Not implemented yet!");
       return null;
   }

   /**
    *
    */
   public void setProperty(String name, Object value) {
       log.warn("Not implemented yet!");
   }
}
