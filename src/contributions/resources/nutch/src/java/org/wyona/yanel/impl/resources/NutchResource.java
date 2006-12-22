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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Category;

import org.apache.nutch.html.Entities;
import org.apache.nutch.metadata.Metadata;

import org.apache.nutch.searcher.Hit;
import org.apache.nutch.searcher.HitDetails;
import org.apache.nutch.searcher.Hits;
import org.apache.nutch.searcher.NutchBean;
import org.apache.nutch.searcher.Query;
import org.apache.nutch.searcher.Summary;
import org.apache.nutch.searcher.Summary.Fragment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.wyona.yanel.core.Path;
import org.wyona.yanel.core.Resource;
import org.wyona.yanel.core.api.attributes.ViewableV1;
import org.wyona.yanel.core.attributes.viewable.View;
import org.wyona.yanel.core.attributes.viewable.ViewDescriptor;
import org.wyona.yanel.core.transformation.I18nTransformer;
import org.wyona.yarep.core.RepositoryException;
import org.wyona.yarep.core.Repository;
import org.wyona.yarep.core.RepositoryFactory;
import org.wyona.yarep.util.RepoPath;

import javax.servlet.ServletContext;
/**
 * 
 */
public class NutchResource extends Resource implements ViewableV1 {

    private static Category log = Category.getInstance(NutchResource.class);
    private final String XML_MIME_TYPE = "application/xml";
    private final String XHTML_MIME_TYPE = "application/xhtml+xml";
    private final String NAME_SPACE = "http://www.wyona.org/yanel/1.0";
    private Configuration configuration = null;
    private File crawlDir = null;
    private Element exceptionElement = null;
    private Element resultsElement = null;
    private String exceptionMessage = null;
    private Document document = null;
    private int start = 0;
    private int hitsPerPage = 10;
    private int numberOfPagesShown = 20;
    private int totalHitCount = 100;
    private long totalHits = 0;
    private String defaultFile = "nutch-default.xml";
    private String localFile = "nutch-local.xml";
    private String defaultLanguage = "en";
    private String searchTerm = "";
    private String show = ""; //default is empty, else show either CACHE, EXPLAIN, ANCHORS
    private String resourceBundle = "nutch";
    private NutchBean nutchBean = null;
    private ServletContext servletContext = null;
    private String cachedMimeType = null;
    private Transformer transformer = null;
    private I18nTransformer i18nTransformer = null;
    
    /**
     * 
     */
    public NutchResource() {
    }

    /**
     * 
     */
    public ViewDescriptor[] getViewDescriptors() {
        return null;
    }

    /**
     * 
     */
    public View getView(Path path, String viewId) {
        return getView(path, viewId, 0, 0, defaultLanguage);
    }

    /**
     * Generate view
     */
    public View getView(Path path, String viewId, int idx, int id, String language) {
        View nutchView = null;
        try {
            getNutchConfiguration();

            resourceBundle = getRTI().getProperty("messageBundle");
            nutchView = new View();
            nutchView.setInputStream(getInputStream(viewId, show, idx, id, language));

            // Set Mime Type
            if(show.equals("cache")) {
                nutchView.setMimeType(cachedMimeType);
            } else if(show.equals("explain") || show.equals("anchors"))  {
                nutchView.setMimeType("text/html");
            } else {
                if (viewId != null && viewId.equals("source")) {
                    nutchView.setMimeType(XML_MIME_TYPE);
                } else {
                    nutchView.setMimeType(XHTML_MIME_TYPE);
                }    
            }
        } catch (Exception e) {
            log.error(e, e);
        }
        return nutchView;
    }

    /**
     * 
     */
    public View getView(HttpServletRequest request, String viewId) {
        servletContext = request.getSession().getServletContext();
        int _start = 0;
        try {
            start = Integer.parseInt(request.getParameter("start"));
        } catch(Exception e) {
            start = _start;
        }
        int _hitsPerPage = 10;
        try {
            hitsPerPage = Integer.parseInt(request.getParameter("hitsPerPage"));
        } catch(Exception e) {
            hitsPerPage = _hitsPerPage;
        }

        String language = request.getParameter("yanel.meta.language");
        if (language == null) {
            language = request.getHeader("Accept-Language");
            if (language != null) {
                log.debug("Use Accept-Language from Request Header: " + language);
                if (language.indexOf(",") > 0) {
                    language = language.substring(0, language.indexOf(","));
                }
            }
        }
        if (language == null) {
            language = defaultLanguage;
        }
        log.debug("Language: " + language);

        int idx = 0;
        try {
            idx = Integer.parseInt(request.getParameter("idx"));
        } catch(Exception e) {
            idx = 0;
        }
        int id = 0;
        try {
            id = Integer.parseInt(request.getParameter("id"));
        } catch(Exception e) {
            id = 0;
        }
        show = request.getParameter("show");
        if (show == null) show = "";
        searchTerm = request.getParameter("query");
        return getView(new Path(request.getServletPath()), viewId, idx, id, language);
    }

    /**
     *  Read Nutch configuration (default and local)
     */
    private void getNutchConfiguration() {
        configuration = new Configuration();
        try {
            String confDir = "file:" + rtd.getConfigFile().getParentFile().getAbsolutePath()
                    + File.separator + "conf";
            log.debug("Conf Dir: " + confDir);
            URL defaultResource = new URL(confDir + File.separator + defaultFile);
            configuration.addDefaultResource(defaultResource);

            URL finalResource = new URL(confDir + File.separator + localFile);
            String nutchConfig = getRTI().getProperty("nutch-config");
            log.debug("Local nutch config: " + nutchConfig);
            if(nutchConfig != null) {
                if(nutchConfig.indexOf("file:") == 0) {
                    finalResource = new URL(nutchConfig);
                } else {
                    log.error("file: protocol is missing: " + nutchConfig);
                }
            }
            configuration.addFinalResource(finalResource);
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }
    }
    
    /**
     * Create DOM document
     * @param searchTerm
     */
    private void getDOMDocument(String searchTerm, String language) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        try {
            DocumentBuilder parser = dbf.newDocumentBuilder();
            document = parser.parse(new java.io.StringBufferInputStream("<nutch:nutch xmlns:nutch=\""+NAME_SPACE+"\" xmlns=\""+NAME_SPACE+"\"/>"));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        Element rootElement = document.getDocumentElement();
        rootElement.setAttributeNS(NAME_SPACE, "language", language);
        if (searchTerm != null && searchTerm.length() > 0) {
            Element queryElement = (Element) rootElement.appendChild(document.createElementNS(NAME_SPACE, "query"));
            queryElement.appendChild(document.createTextNode(searchTerm));
            resultsElement = (Element) rootElement.appendChild(document.createElementNS(NAME_SPACE, "results"));
            resultsElement.setAttributeNS(NAME_SPACE, "start", "" + start);
            try {
                crawlDir = new File(configuration.get("searcher.dir"));
            } catch (Exception e) {
                log.error(e);
                exceptionElement = (Element) resultsElement.appendChild(document.createElementNS(NAME_SPACE, "exception"));
                exceptionElement.appendChild(document.createTextNode(e.getMessage()));
            }
            if (crawlDir != null) {
                createDocument4SearchResult(searchTerm, start, hitsPerPage);
            }
        } else {
            rootElement.appendChild(document.createElementNS(NAME_SPACE, "no-query"));
        }
    }
    
    /**
     * Generate response depending on show parameter
     * @param show cache, explain, anchors and actual search results
     */
    private InputStream getInputStream(String viewId, String show, int idx, int id, String language) {
        if(show.equals("cache")){
            return new StringBufferInputStream(getCachedContent(idx, id));
        } else if(show.equals("explain")) {
            return createExplanationDocument4SearchResult(idx, id, searchTerm, language);
        } else if(show.equals("anchors")) {
            return createAnchorsDocument4SearchResult(idx, id, searchTerm, language);
        } else {
            getDOMDocument(searchTerm, language);
            return transformedInputStream(viewId, searchTerm, language);
        }
    }

    /**
     * Generate results page as XHTML
     * @param viewId
     * @param searchTerm
     * @return
     */
    private InputStream transformedInputStream(String viewId, String searchTerm, String language) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            if (viewId != null && viewId.equals("source")) {
                transformer = TransformerFactory.newInstance().newTransformer();
                transformer.transform(new javax.xml.transform.dom.DOMSource(document), new StreamResult(byteArrayOutputStream));
                return new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
            } else {
                File xsltFile = org.wyona.commons.io.FileUtil.file(rtd.getConfigFile().getParentFile()
                        .getAbsolutePath(), "xslt" + File.separator + "result2xhtml.xsl");
                transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(xsltFile));
                transformer.setParameter("yanel.path.name", getPath().getName());
                // TODO: Remove the trailing slash ...
                transformer.setParameter("yanel.path", getRealm().getMountPoint() + getPath().toString());
                log.debug("Yanel Path: " + getRealm().getMountPoint() + getPath());
                transformer.setParameter("yanel.back2context", backToRoot(new Path(getRealm().getMountPoint().toString() + getPath().toString()), ""));
                log.debug("Back 2 context: " + getRealm().getMountPoint() + getPath() + ", " + backToRoot(new Path(getRealm().getMountPoint().toString() + getPath().toString()), ""));
                transformer.setParameter("yarep.back2realm", backToRoot(getPath(), ""));
                log.debug("Back 2 realm: " + getPath() + ", " + backToRoot(getPath(), ""));
                transformer.transform(new javax.xml.transform.dom.DOMSource(document), new StreamResult(byteArrayOutputStream));
                InputStream inputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
                i18nTransformer = new I18nTransformer(resourceBundle, language);
                SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
                saxParser.parse(inputStream, i18nTransformer);
                return applyGlobalXslIfExists(i18nTransformer.getInputStream(), searchTerm, language);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * 
     * @param inputStream
     * @param searchTerm
     * @return
     */
    private InputStream applyGlobalXslIfExists(InputStream inputStream, String searchTerm, String language) {
        StreamSource streamSource = null;
        try {
            streamSource = getXSLTStreamSource();
            if(streamSource != null) {
                transformer = TransformerFactory.newInstance().newTransformer(streamSource);
                transformer.setParameter("yanel.path.name", getPath().getName());
                transformer.setParameter("yanel.path", getPath().toString());
                transformer.setParameter("yanel.back2context", backToRoot(getPath(), ""));
                transformer.setParameter("yarep.back2realm", backToRoot(getPath(), ""));
                transformer.setParameter("hitsPerPage", "" + hitsPerPage);
                transformer.setParameter("totalHits", "" + totalHits);
                transformer.setParameter("query", "" + searchTerm);
                transformer.setParameter("start", "" + start);
                transformer.setParameter("yanel.meta.lanugage", language);
                transformer.setParameter("show", show);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                transformer.transform(new StreamSource(inputStream), new StreamResult(byteArrayOutputStream));
                inputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
                log.debug("Language: " + language);
                i18nTransformer = new I18nTransformer(resourceBundle, language);
                SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
                saxParser.parse(inputStream, i18nTransformer);
                return i18nTransformer.getInputStream(); 
            } else {
                return inputStream;
            }     
        } catch(Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * Get cached content for a specific search result
     * @param idx
     * @param id
     * @return
     */
    private String getCachedContent(int idx, int id) {
        log.debug("idx: " + idx);
        log.debug("id: " + id);
        String content = "NULL";
        try {
            nutchBean = NutchBean.get(servletContext, configuration);
            Hit hit = new Hit(idx, id); 
            HitDetails details = nutchBean.getDetails(hit);
            Metadata metaData = nutchBean.getParseData(details).getContentMeta();
            String contentType = (String) metaData.get(Metadata.CONTENT_TYPE);
            log.error("contentType: " + contentType);
            cachedMimeType = contentType;
            if (contentType.startsWith("text/html")) {
                String encoding = (String) metaData.get("CharEncodingForConversion");
                log.debug("encoding: " + encoding);
                if (encoding != null) {
                    try {
                        content = new String(nutchBean.getContent(details), encoding);
                    } catch (Exception e) {
                        content = new String(nutchBean.getContent(details), "windows-1252");
                    }
                } else {
                    content = new String(nutchBean.getContent(details));
                }
            } else {
                content = new String(nutchBean.getContent(details));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        log.debug("content:\n" + content);
        return content;
    }
    
    /**
     * 
     * @param idx
     * @param id
     * @param searchTerm
     * @param language
     * @return
     */
    private InputStream createExplanationDocument4SearchResult(int idx, int id, String searchTerm, String language) {
        try {
            nutchBean = NutchBean.get(servletContext, configuration);
            Hit hit = new Hit(idx, id);
            Query query = Query.parse(searchTerm, language, configuration);
            String content = "<html xmlns:xhtml=\"http://www.w3.org/1999/xhtml\" " +
                    "xmlns=\"http://www.w3.org/1999/xhtml\">" +
                    "<head><title><i18n:message key=\"scoreExplanation\"/>: " + searchTerm + "</title></head>" +
                    "<body><div id=\"results\">" +
                    "<h3><i18n:message key=\"page\"/></h3>" +
                    replaceAmpersand(nutchBean.getDetails(hit).toHtml()) + 
                    "<h3><i18n:message key=\"scoreForQuery\"/>" + query + "</h3>" + 
                    nutchBean.getExplanation(query, hit) + 
                    "</div></body></html>"; 
            I18nTransformer i18nTransformer = new I18nTransformer(resourceBundle, language);
            SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
            saxParser.parse(new StringBufferInputStream(content), i18nTransformer);
            return applyGlobalXslIfExists(i18nTransformer.getInputStream(), searchTerm, language);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * Generate anchors page
     * @param idx
     * @param id
     * @param searchTerm
     * @param language
     * @return
     */
    private InputStream createAnchorsDocument4SearchResult(int idx, int id, String searchTerm, String language) {
        try {
            nutchBean = NutchBean.get(servletContext, configuration);
            Hit hit = new Hit(idx, id);
            HitDetails details = nutchBean.getDetails(hit);
            String listItems = "";
            String[] anchors = nutchBean.getAnchors(details);
            if(anchors != null) {
                for(int i=0; i<anchors.length; i++) {
                    listItems += "<li>" + Entities.encode(anchors[i]) + "</li>";
                }
            }
            String content = "<html xmlns:xhtml=\"http://www.w3.org/1999/xhtml\" " +
                    "xmlns=\"http://www.w3.org/1999/xhtml\">" +
                    "<head><title><i18n:message key=\"anchors\"/>: " + searchTerm + "</title></head>" +
                    "<body><div id=\"results\"><h3><i18n:message key=\"anchors\"/></h3>" +
                    replaceAmpersand(details.getValue("url"));
            if(anchors != null) {
                content += "<ul>" + listItems + "</ul>"; 
            }
            content += "</div></body></html>"; 
            log.debug("content:\n" + content);
            I18nTransformer i18nTransformer = new I18nTransformer(resourceBundle, language);
            SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
            saxParser.parse(new StringBufferInputStream(content), i18nTransformer);
            return applyGlobalXslIfExists(i18nTransformer.getInputStream(), searchTerm, language);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * 
     * @param path
     * @param backToRoot
     * @return
     */
    private String backToRoot(Path path, String backToRoot) {
        org.wyona.commons.io.Path parent = path.getParent();
        if (parent != null && !isRoot(parent)) {
            return backToRoot(new Path(parent.toString()), backToRoot + "../");
        }
        return backToRoot;
    }

    /**
     * 
     * @param path
     * @return
     */
    private boolean isRoot(org.wyona.commons.io.Path path) {
        if (path.toString().equals(File.separator)) return true;
        return false;
    }
    
    /**
     * This method creates a document for the given searchTerm starting at result <start> 
     * and stores the informations within a xml 
     * @param searchTerm 
     * @param start position of found results for searchTerm 
     */
    private void createDocument4SearchResult(String searchTerm, int start, int hitsPerPage) {
        try {
            if (!crawlDir.isDirectory()) {
                exceptionElement = (Element) resultsElement.appendChild(document.createElementNS(NAME_SPACE, "exception"));
                exceptionMessage = "noSuchCrawlDirectory#" + crawlDir;
                exceptionElement.appendChild(document.createTextNode(exceptionMessage));
                log.warn(exceptionMessage);
                return;
            } else {
                getRefineList();
                
                nutchBean = new NutchBean(configuration);
                Query query = Query.parse(searchTerm, configuration);
                Hits hits = nutchBean.search(query, totalHitCount);
                totalHits = hits.getTotal();
                int range = (int) Math.min(hits.getTotal() - start, hitsPerPage);
                resultsElement.setAttributeNS(NAME_SPACE, "hitsPerPage", "" + hitsPerPage);
                resultsElement.setAttributeNS(NAME_SPACE, "totalHits", "" + totalHits);
                resultsElement.setAttributeNS(NAME_SPACE, "currentPageNo", "" + ((start / hitsPerPage) + 1));
                resultsElement.setAttributeNS(NAME_SPACE, "numberOfPagesShown", "" + numberOfPagesShown);
                Hit[] show = hits.getHits(start, range);
                HitDetails[] details = nutchBean.getDetails(show);
                Summary[] summaries = nutchBean.getSummary(details, query);
                Element fetchedDateElement = null;
                Element segmentElement = null;
                Element digestElement = null;
                Element urlElement = null;
                Element titleElement = null;
                Element hitIndexDocNoElement = null;
                Element hitDedupValueElement = null;
                Element hitIndexNoElement = null;
                Element fragmentsElement = null;
                Element fragmentElement = null;
                for (int i = 0; i < show.length; i++) {
                    Element resultElement = (Element) resultsElement.appendChild(document.createElementNS(NAME_SPACE, "result"));
                    resultElement.setAttributeNS(NAME_SPACE, "index", "" + (i + 1));
                    fetchedDateElement = (Element) resultElement.appendChild(document.createElementNS(NAME_SPACE, "fetchedDate"));
                    Date fetchedDate = new Date(nutchBean.getFetchDate(details[i]));
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.S");
                    String formattedDate = simpleDateFormat.format(fetchedDate);
                    fetchedDateElement.appendChild(document.createTextNode(formattedDate));
                    segmentElement = (Element) resultElement.appendChild(document.createElementNS(NAME_SPACE, "segment"));
                    segmentElement.appendChild(document.createTextNode(details[i].getValue("segment")));
                    digestElement = (Element) resultElement.appendChild(document.createElementNS(NAME_SPACE, "digest"));
                    digestElement.appendChild(document.createTextNode(details[i].getValue("digest")));
                    urlElement = (Element) resultElement.appendChild(document.createElementNS(NAME_SPACE, "url"));
                    urlElement.appendChild(document.createTextNode(details[i].getValue("url")));
                    titleElement = (Element) resultElement.appendChild(document.createElementNS(NAME_SPACE, "title"));
                    titleElement.appendChild(document.createTextNode(details[i].getValue("title")));
                    hitIndexDocNoElement = (Element) resultElement.appendChild(document.createElementNS(NAME_SPACE, "hitIndexDocNo"));
                    hitIndexDocNoElement.appendChild(document.createTextNode(""
                            + hits.getHit(i).getIndexDocNo()));
                    hitDedupValueElement = (Element) resultElement.appendChild(document.createElementNS(NAME_SPACE, "hitDedupValue"));
                    hitDedupValueElement.appendChild(document.createTextNode(hits.getHit(i)
                            .getDedupValue()));
                    hitIndexNoElement = (Element) resultElement.appendChild(document.createElementNS(NAME_SPACE, "hitIndexNo"));
                    hitIndexNoElement.appendChild(document.createTextNode(""
                            + hits.getHit(i).getIndexNo()));
                    fragmentsElement = (Element) resultElement.appendChild(document.createElementNS(NAME_SPACE, "fragments"));
                    Fragment[] fragments = summaries[i].getFragments();

                    for (int j = 0; j < fragments.length; j++) {
                        fragmentElement = (Element) fragmentsElement.appendChild(document.createElementNS(NAME_SPACE, "fragment"));
                        fragmentElement.setAttributeNS(NAME_SPACE, "highlight", "" + fragments[j].isHighlight());
                        fragmentElement.setAttributeNS(NAME_SPACE, "ellipsis", "" + fragments[j].isEllipsis());
                        // Also see org.apache.nutch.searcher.Summary.toHtml()
                        String fragmentValue = replaceAmpersand(fragments[j].toString());
                        fragmentValue = fragmentValue.replaceAll("<", "&lt;");
                        fragmentValue = fragmentValue.replaceAll(">", "&gt;");
                        fragmentElement.appendChild(document.createCDATASection(fragmentValue));
                        // TODO: Why does this not work for all cases? ...
                        //fragmentElement.appendChild(document.createCDATASection(replaceAmpersand(fragments[j].getText())));
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
    
    /**
     * Use OWL to refine query
     */
    private List getRefineList() {
        org.apache.nutch.ontology.Ontology ontology = null;
        try {
            // Configuration nutchConf = NutchConfiguration.get(application);
            String urls = configuration.get("extension.ontology.urls");
            log.error("DEBUG: extension.ontology.urls: " + urls);

/*
            // TODO: null is being returned!
            ontology = new org.apache.nutch.ontology.OntologyFactory(configuration).getOntology();
            if (urls==null || urls.trim().equals("")) {
                // ignored siliently
            } else {
                log.error("is ontology null? : " + ontology);
                ontology.load(urls.split("\\s+"));
            }
*/
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
         
        List refineList = new ArrayList();
        if (ontology != null) {
            Iterator iter = ontology.subclasses(searchTerm);
            while (iter.hasNext()) {
                String nextItem = (String)iter.next();
                log.error("iterating: --> " + nextItem);  
                // refineList.add((String)iter.next());
                refineList.add(nextItem);
            }
        } else {
            log.warn("Ontology is null");
        }
        return refineList;
    }

    /**
     * Get XSLT Stream Source of global xslt
     * @param path
     * @param repo
     * @return StreamSource
     */
    private StreamSource getXSLTStreamSource() throws Exception {
        String xsltPath = getRTI().getProperty("xslt");
        log.debug("XSLT: " + xsltPath);
        if (xsltPath != null) {
            return new StreamSource(getRealm().getRepository().getInputStream(new org.wyona.yarep.core.Path(xsltPath)));
        } else {
            return null;
        }
    }

    /**
     * This method replaces all occurences of '&' but not '&amp;' with '&amp;'
     * @param inputString with or without '&'
     * @return replaced ampersands as string
     */
    private String replaceAmpersand(String inputString) {
        String [] tokens = inputString.split("&amp;");
        String replacedAmpersand = null;
        if(inputString.indexOf("&amp;") == -1) {
            replacedAmpersand = inputString.replaceAll("&", "&amp;");
        } else {
            replacedAmpersand = "";
            for(int i = 0; i < tokens.length; i++) {
                replacedAmpersand += tokens[i].replaceAll("&", "&amp;") + "&amp;";
            }
        }
        log.debug("[" + inputString + "] replaced with [" + replacedAmpersand + "]");
        return replacedAmpersand;
    }
    
    /**
     * 
     * @return
     */
    protected RepositoryFactory getRepositoryFactory() {
        return yanel.getRepositoryFactory("DefaultRepositoryFactory");
    }
    
    /**
     * this method will display the input stream for debugging purposes
     * @param inputStream
     */
    private void debugInputStream(InputStream inputStream) {
        java.io.InputStreamReader inR = new java.io.InputStreamReader(inputStream) ; 
        java.io.BufferedReader buf = new java.io.BufferedReader (inR) ; 
        String line; 
        try {
            while((line = buf.readLine()) != null) {  
                log.error("::::" + line) ; 
            }
            inputStream.close();   
        } catch (IOException e) {
            e.printStackTrace();
        }  
    }
}
