package org.movieos.feeder.api;
// https://github.com/eclipse/egit-github/blob/master/org.eclipse.egit.github.core/src/org/eclipse/egit/github/core/client/PageLinks.java

/*******************************************************************************
 *  Copyright (c) 2011 GitHub Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *    Kevin Sawicki (GitHub Inc.) - initial API and implementation
 *******************************************************************************/

import okhttp3.Response;

/**
 * Page link class to be used to determine the links to other pages of request
 * responses encoded in the current response. These will be present if the
 * result set size exceeds the per page limit.
 */
public class PageLinks {

    private static final String HEADER_LINK = "Links";
    private static final String HEADER_NEXT = "X-Next";
    private static final String HEADER_LAST = "X-Last";
    private static final String META_REL = "rel";
    private static final String META_LAST = "last";
    private static final String META_NEXT = "next";
    private static final String META_FIRST = "first";
    private static final String META_PREV = "prev";

    private static final String DELIM_LINKS = ",";
    private static final String DELIM_LINK_PARAM = ";";

    private String first;
    private String last;
    private String next;
    private String prev;

    /**
     * Parse links from executed method
     *
     * @param response
     */
    public PageLinks(Response response) {
        String linkHeader = response.header(HEADER_LINK);
        if (linkHeader != null) {
            String[] links = linkHeader.split(DELIM_LINKS);
            for (String link : links) {
                String[] segments = link.split(DELIM_LINK_PARAM);
                if (segments.length < 2)
                    continue;

                String linkPart = segments[0].trim();
                if (!linkPart.startsWith("<") || !linkPart.endsWith(">")) //$NON-NLS-2$
                    continue;
                linkPart = linkPart.substring(1, linkPart.length() - 1);

                for (int i = 1; i < segments.length; i++) {
                    String[] rel = segments[i].trim().split("=");
                    if (rel.length < 2 || !META_REL.equals(rel[0]))
                        continue;

                    String relValue = rel[1];
                    if (relValue.startsWith("\"") && relValue.endsWith("\"")) //$NON-NLS-2$
                        relValue = relValue.substring(1, relValue.length() - 1);

                    if (META_FIRST.equals(relValue))
                        first = linkPart;
                    else if (META_LAST.equals(relValue))
                        last = linkPart;
                    else if (META_NEXT.equals(relValue))
                        next = linkPart;
                    else if (META_PREV.equals(relValue))
                        prev = linkPart;
                }
            }
        } else {
            next = response.header(HEADER_NEXT);
            last = response.header(HEADER_LAST);
        }
    }

    /**
     * @return first
     */
    public String getFirst() {
        return first;
    }

    /**
     * @return last
     */
    public String getLast() {
        return last;
    }

    /**
     * @return next
     */
    public String getNext() {
        return next;
    }

    /**
     * @return prev
     */
    public String getPrev() {
        return prev;
    }
}