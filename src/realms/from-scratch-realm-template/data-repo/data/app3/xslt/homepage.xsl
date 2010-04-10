<?xml version="1.0" encoding="UTF-8" ?>

<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns="http://www.w3.org/1999/xhtml"
  xmlns:xhtml="http://www.w3.org/1999/xhtml"
  xmlns:xi="http://www.w3.org/2001/XInclude"
  xmlns:dc="http://purl.org/dc/elements/1.1/"
  exclude-result-prefixes="xhtml dc">

  <xsl:param name="yanel.path.name" select="'NAME_IS_NULL'"/>
  <xsl:param name="yanel.path" select="'PATH_IS_NULL'"/>
  <xsl:param name="yanel.back2context" select="'BACK2CONTEXT_IS_NULL'"/>
  <xsl:param name="yarep.back2realm" select="'BACK2REALM_IS_NULL'"/>
  <xsl:param name="yanel.toolbar-status" select="'TOOLBAR-STATUS_IS_NULL'"/>
  <xsl:param name="yanel.reservedPrefix" select="'RESERVED-PREFIX_IS_NULL'"/>
  <xsl:variable name="name-without-suffix" select="substring-before($yanel.path.name, '.')"/>
  <xsl:param name="language" select="'LANGUAGE_IS_NULL'"/>
  <xsl:param name="content-language" select="'CONTENT_LANGUAGE_IS_NULL'"/>
  <xsl:param name="languages" select="'LANGUAGES_IS_NULL'"/>

<xsl:template match="/">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <meta content="Wyona Yanel" name="generator"/>
    <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
    <title>Wyona</title>
    <link rel="stylesheet" type="text/css" href="{$yarep.back2realm}app3/css/all.css" media="all" />
    <!--[if lt IE 7]><link rel="stylesheet" type="text/css" href="{$yarep.back2realm}app3/css/ie6.css" media="screen"/><![endif]-->

    <!-- Javascript for gallery carusel -->
    <script type="text/javascript" src="{$yarep.back2realm}app3/js/jquery-1.3.2.min.js"></script>
    <script type="text/javascript" src="{$yarep.back2realm}app3/js/jquery.galleryScroll.1.5.2.js"></script>
    <script type="text/javascript" src="{$yarep.back2realm}app3/js/carusel.js"></script>
  </head>
<body>
	<div id="wrapper">
		<div id="main" class="home">
			<div class="main-t"></div>
			<div class="main-c">
				<div class="main-content">
					<div class="gallery">
						<div class="g1">
							<ul>
								<li>
									<a href="http://www.nzz.ch"><img src="{$yarep.back2realm}app3/images/img-2.jpg" alt="" width="788" height="202" /></a>
									<div class="gallery-text">
										<div class="holder">
											<strong class="logo-gallery">globus savoir vivre</strong>
											<a href="#" class="more">Globus.ch driving offline business <br /> through online channel <img src="{$yarep.back2realm}app3/images/arrow-6.gif" alt="" width="26" height="26" /></a>
										</div>
									</div>
								</li>
								<li>
									<a href="http://www.wyona.com"><img src="{$yarep.back2realm}app3/images/img-2.jpg" alt="" width="788" height="202" /></a>
								</li>
								<li>
									<a href="http://www.wyona.com"><img src="{$yarep.back2realm}app3/images/img-2.jpg" alt="" width="788" height="202" /></a>
								</li>
								<li>
									<a href="http://www.wyona.com"><img src="{$yarep.back2realm}app3/images/img-2.jpg" alt="" width="788" height="202" /></a>
								</li>
								<li>
									<a href="http://www.wyona.com"><img src="{$yarep.back2realm}app3/images/img-2.jpg" alt="" width="788" height="202" /></a>
								</li>
							</ul>
						</div>
						<a href="#" class="link-prev">prev</a>
						<a href="#" class="link-next">next</a>
						<div class="swicher">
							
						</div>
					</div>
					<div id="tree-columns">
						<div class="content-box">
							<div class="container">
								<h1>Latest News and Opinions</h1>
								<ul class="content-nav">
							</div>
							<div class="quotes-box">
								<h2>Quotes</h2>
								<blockquote>
									<div>
										<div class="holder">
											<div class="frame">
												<q>“Driving business online is all about qualified traffic and conversion”</q>
											</div>
										</div>
										<cite>
											<img src="{$yarep.back2realm}app3/images/img-3.jpg" alt="" width="69" height="69" />
											<span>Andy Brügger, <br />Globus</span>
										</cite>
									</div>
								</blockquote>
								<blockquote>
									<div>
										<div class="holder">
											<div class="frame">
												<q>“Lorem ipsum dolor et samet massa maretima”</q>
											</div>
										</div>
										<cite>
											<img src="{$yarep.back2realm}app3/images/img-4.jpg" alt="" width="69" height="69" />
											<span>David Zimmerli, <br />NAZ</span>
										</cite>
									</div>
								</blockquote>
							</div>
						</div>
						<div class="side-columns">
							<h2>What would you like to do?</h2>
							<ul class="user-action">
							</ul>
						</div>
				</div>
			</div>
			<div class="main-b"></div>
		</div>
			<div class="add-nav-box">
				<form action="#" class="header-forms">
					<fieldset>
						<div class="text">
							<input type="text" value="" />
						</div>
						<input type="submit" value="Search" class="submit" />
					</fieldset>
				</form>
				<ul class="add-nav">
					<li><a href="#">Extranet</a></li>
			</div>
			<div class="header-box">
				<strong class="logo">
					<a href="#">wyona</a>
				</strong>
				<ul id="nav">
					<li><a href="#">Solutions</a></li>
			</div>
		</div>
			<ul class="footer-nav">
				<li class="contact">Contact: <address>Wyona AG, Hardstrasse 219, CH-8005 Zurich, +41 44 272 91 61</address></li>
		</div>
</body>
</html>
</xsl:template>

</xsl:stylesheet>