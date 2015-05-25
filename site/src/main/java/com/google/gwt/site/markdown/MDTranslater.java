/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.gwt.site.markdown;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.google.gwt.site.markdown.fs.MDNode;
import com.google.gwt.site.markdown.fs.MDParent;
import com.google.gwt.site.markdown.pegdown.MarkdownParser;
import com.google.gwt.site.markdown.toc.TocCreator;
import com.google.gwt.site.markdown.velocity.VelocityWrapper;
import com.google.gwt.site.markdown.velocity.VelocityWrapperFactory;

public class MDTranslater {
    private static final String SEPARATOR = File.separator;

    private final MarkdownParser markdownParser;
    private final TocCreator tocCreator;
    private final MarkupWriter writer;
    private final String template;
    private final String editRootUrl;
    private final VelocityWrapperFactory velocityFactory;

    public MDTranslater(
            MarkdownParser markdownParser,
            TocCreator tocCreator,
            MarkupWriter writer,
            String template,
            String editRootUrl,
            VelocityWrapperFactory velocityFactory) {
        this.markdownParser = markdownParser;
        this.tocCreator = tocCreator;
        this.writer = writer;
        this.template = template;
        this.editRootUrl = editRootUrl;
        this.velocityFactory = velocityFactory;
    }

    public void render(MDParent root) throws TranslaterException {
        renderTree(root, root);
    }

    private void renderTree(MDNode node, MDParent root) throws TranslaterException {

        if (node.isFolder()) {
            MDParent mdParent = node.asFolder();

            List<MDNode> children = mdParent.getChildren();
            for (MDNode mdNode : children) {
                renderTree(mdNode, root);
            }
        } else {
            String fileContent = getNodeContent(node.getPath());

            String content;
            if (isMarkdown(node)) {
                content = markdownParser.toHtml(fileContent);
            } else {
                content = fileContent;
            }

            String toc = tocCreator.createTocForNode(root, node);

            StringBuilder relativePath = new StringBuilder("./");
            for (int i = 1; i < node.getDepth(); i++) {
                relativePath.append("../");
            }

            String html = fillTemplate(
                    content,
                    adjustRelativePath(toc, relativePath.toString()),
                    node);

            writer.writeHTML(node, adjustRelativePath(html, relativePath.toString()));
        }
    }

    private boolean isMarkdown(MDNode node) {
        return node.getPath().endsWith(".md");
    }

    private String fillTemplate(String html, String toc, MDNode node) {
        VelocityWrapper velocityWrapper = velocityFactory.create(template);

        velocityWrapper.put("content", html);
        velocityWrapper.put("toc", toc);
        velocityWrapper.put("node", node);
        velocityWrapper.put("editLink", getEditUrl(node.getPath()));

        return velocityWrapper.generate();
    }

    private String getEditUrl(String path) {
        if (editRootUrl == null) {
            return null;
        }

        int index = path.indexOf(SEPARATOR + "src" + SEPARATOR);
        String url = path.substring(index + 1).replace(SEPARATOR, "/");
        return "<a class=\"icon_editGithub\" href=\"" + editRootUrl + url + "\"></a>";
    }

    protected String adjustRelativePath(String html, String relativePath) {
        // Just using Regexp to add relative paths to certain urls.
        // If we wanted to support a more complicated syntax
        // we could parse the template with some library like jsoup
        return html.replaceAll("(href|src|property=\"og:image\" content)=(['\"])(?:(?:/+)|(?!(?:[a-z]+:|#)))(.*?)(\\2)",
                "$1='" + relativePath + "$3'");
    }

    private String getNodeContent(String path) throws TranslaterException {
        try {
            return FilesUtils.getStringFromFile(new File(path));
        } catch (IOException e1) {
            throw new TranslaterException("can not load content from file: '" + path + "'", e1);
        }
    }
}
