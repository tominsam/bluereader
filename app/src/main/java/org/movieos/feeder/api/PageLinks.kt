package org.movieos.feeder.api

// Based on https://github.com/eclipse/egit-github/blob/master/org.eclipse.egit.github.core/src/org/eclipse/egit/github/core/client/PageLinks.java

import okhttp3.Response

class PageLinks(response: Response) {

    var first: String? = null
        private set

    var last: String? = null
        private set

    var next: String? = null
        private set

    var prev: String? = null
        private set

    init {
        val linkHeader = response.header(HEADER_LINK)
        if (linkHeader != null) {
            val links = linkHeader.split(DELIM_LINKS.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (link in links) {
                val segments = link.split(DELIM_LINK_PARAM.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (segments.size < 2)
                    continue

                var linkPart = segments[0].trim { it <= ' ' }
                if (!linkPart.startsWith("<") || !linkPart.endsWith(">"))
                //$NON-NLS-2$
                    continue
                linkPart = linkPart.substring(1, linkPart.length - 1)

                for (i in 1..segments.size - 1) {
                    val rel = segments[i].trim { it <= ' ' }.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (rel.size < 2 || META_REL != rel[0])
                        continue

                    var relValue = rel[1]
                    if (relValue.startsWith("\"") && relValue.endsWith("\""))
                    //$NON-NLS-2$
                        relValue = relValue.substring(1, relValue.length - 1)

                    if (META_FIRST == relValue)
                        first = linkPart
                    else if (META_LAST == relValue)
                        last = linkPart
                    else if (META_NEXT == relValue)
                        next = linkPart
                    else if (META_PREV == relValue)
                        prev = linkPart
                }
            }
        } else {
            next = response.header(HEADER_NEXT)
            last = response.header(HEADER_LAST)
        }
    }

    companion object {

        private val HEADER_LINK = "Links"
        private val HEADER_NEXT = "X-Next"
        private val HEADER_LAST = "X-Last"
        private val META_REL = "rel"
        private val META_LAST = "last"
        private val META_NEXT = "next"
        private val META_FIRST = "first"
        private val META_PREV = "prev"

        private val DELIM_LINKS = ","
        private val DELIM_LINK_PARAM = ";"
    }
}