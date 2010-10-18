package org.grails.plugin.resource

import org.apache.commons.logging.LogFactory

class CSSLinkProcessor {
    
    def log = LogFactory.getLog(CSSLinkProcessor)

    static CSS_URL_PATTERN = ~/(url\s*\(['"]?\s*)(.+?)(\s*['"]?\s*\))/
    
    boolean isCSSRewriteCandidate(resource, resourceService) {
        def enabled = resourceService.config.rewrite.css instanceof Boolean ? resourceService.config.rewrite.css : true
        def yes = enabled && (resource.contentType == "text/css" || resource.tagAttributes?.type == "css")
        if (log.debugEnabled) {
            log.debug "Resource ${resource.actualUrl} being CSS rewritten? $yes"
        }
        return yes
    }
    
    /**
     * Find all url() and fix up the url if it is not absolute
     * NOTE: This needs to run after any plugins that move resources around, but before any that obliterate
     * the content i.e. before minify or gzip
     */
    void process(ResourceMeta resource, resourceService, Closure urlMapper) {
        
        if (!isCSSRewriteCandidate(resource, resourceService)) {
            return
        }
        
        // Move existing to tmp file, then write to the correct file
        def origFile = new File(resource.processedFile.toString()+'.tmp')
        
        // Make sure it doesn't exist already
        new File(origFile.toString()).delete() // On MS Windows if we don't do this origFile gets corrupt after delete
        
        resource.processedFile.renameTo(origFile)
        def rewrittenFile = resource.processedFile
        if (log.debugEnabled) {
            log.debug "Pre-processing CSS resource ${resource.sourceUrl} to rewrite links"
        }

        // Replace all urls to resources we know about to their processed urls
        rewrittenFile.withPrintWriter("utf-8") { writer ->
           origFile.eachLine { line ->
               def fixedLine = line.replaceAll(CSS_URL_PATTERN) { Object[] args ->
                   def prefix = args[1]
                   def originalUrl = args[2].trim()
                   def suffix = args[3]

                   return urlMapper(prefix, originalUrl, suffix)
               }
               writer.println(fixedLine)
            }
        }  
        
        // Delete the temp file
        origFile.delete()      
    }
}