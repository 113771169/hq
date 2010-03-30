package org.hyperic.hq.hqu.rendit

import org.hyperic.hq.hqu.rendit.i18n.BundleMapFacade
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

/**
 * Called by the dispatcher to invoke a single method on a class
 */
class InvokeMethodDispatcher {
	Log log = LogFactory.getLog(InvokeMethodDispatcher.class)
	
	def invoke(pluginDir, invokeArgs) {
	    def loader = Thread.currentThread().getContextClassLoader();
	    loader.addURL(pluginDir.toURL())
        def o = Class.forName(invokeArgs.className, true, loader).newInstance() 
        
        log.debug "Invoking ${invokeArgs.methodName} on ${invokeArgs.className}"
        o.invokeMethod(invokeArgs.methodName, invokeArgs.args as Object[])
	}
}
