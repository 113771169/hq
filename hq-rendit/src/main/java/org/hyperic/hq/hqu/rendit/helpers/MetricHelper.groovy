package org.hyperic.hq.hqu.rendit.helpers

import org.hyperic.hq.context.Bootstrap;
import org.hyperic.hq.measurement.server.session.MeasurementTemplate
import org.hyperic.hq.measurement.shared.MeasurementManager;
import org.hyperic.hq.measurement.shared.TemplateManager;
import org.hyperic.hq.measurement.server.session.MeasurementTemplateSortField
import org.hyperic.hibernate.PageInfo
import org.hyperic.hq.authz.server.session.AuthzSubject
import org.hyperic.hq.measurement.server.session.Measurement
import org.hyperic.util.pager.PageControl

class MetricHelper extends BaseHelper {
    private tmplMan = Bootstrap.getBean(TemplateManager.class)
    private measMan = Bootstrap.getBean(MeasurementManager.class)

    MetricHelper(AuthzSubject user) {
        super(user)
    }

    /**
     * General purpose utility method for finding metrics and metric 
     * templates.  Note that these are the metadata for metrics, not the
     * actual metric data itself.
     *
     * Optional arguments:
     *    'user',      defaults to the current user {@link AuthzSubject} 
     *    'permCheck', defaults to true (check user permission)
     *
     *
     * To find all metric templates:     find all: 'templates'
     *    for a specific resource type:  find all: 'templates', resourceType: 'Linux' 
     *        or                      :  find all: 'templates', resourceType: 'regex:Win.*' 
     */
     def find(Map args) {
         args = args + [:]
         ['all', 'withPaging', 'resourceType', 'enabled', 'entity'].each {
             args.get(it, null)
         }
         args.get('user', user)
         args.get('permCheck', true)

         if (!args.permCheck && !args.user.isSuperUser()) {
             args.user = overlord
         }

         if (args.all == 'templates') {
             if (args.withPaging == null) {
                 args.withPaging =
                     PageInfo.getAll(MeasurementTemplateSortField.TEMPLATE_NAME,
                                     true) 
             }

             def filter = {it}
             def resourceType = args.resourceType
             if (resourceType && resourceType.startsWith('regex')) {
                 def regex = ~resourceType[6..-1]
                 
                 // XXX:  This does not page correctly!
                 return tmplMan.findTemplates(args.user, args.withPaging,
                                              args.enabled).grep {
                     it.monitorableType.name ==~ regex 
                 }
             } else if (resourceType) {
                 return tmplMan.findTemplatesByMonitorableType(args.user,
                                                               args.withPaging,
                                                               resourceType,
                                                               args.enabled)
             } else {
                 return tmplMan.findTemplates(args.user, args.withPaging,
                                              args.enabled)
             }
         } else if (args.all == 'metrics') {
             // XXX: This actually only finds the enabled measurements, need
             // to find all regardless of enablement
             return measMan.findMeasurements(args.user, args.entity, null,
                                             PageControl.PAGE_ALL)
         }
         
         throw new IllegalArgumentException("Unsupported find args")
     }

     MeasurementTemplate findTemplateById(int id) {
         tmplMan.getTemplate(id)
     }

     Measurement findMeasurementById(int id) {
         measMan.getMeasurement(id)
     }

    /**
     * @deprecated Use MetricCategory.
     */
     def setDefaultInterval(int id, long interval) {
         Integer[] tmpls = new Integer[1]
         tmpls[0] = id
         tmplMan.updateTemplateDefaultInterval(user, tmpls, interval)
     }

    /**
     * @deprecated Use MetricCategory
     */
     def setDefaultIndicator(int id, boolean on) {
         def tmpl = findTemplateById(id)
         tmplMan.setDesignated(tmpl, on)
     }

    /**
     * @deprecated Use MetricCategory
     */
     def setDefaultOn(int id, boolean on) {
         Integer[] tmpls = new Integer[1]
         tmpls[0] = id
         tmplMan.setTemplateEnabledByDefault(user, tmpls, on)
     }

    /**
     * @deprecated Use MetricCategory
     */
     def disableMeasurement(Integer mId) {
        measMan.disableMeasurement(user, mId);
     }

    /**
     * @deprecated Use MetricCategory
     */
     def enableMeasurement(Integer mId, Long interval) {
        measMan.enableMeasurement(user, mId, interval);
     }

    /**
     * @deprecated Use MetricCategory
     */
     def updateMeasurementInterval(Integer mId, Long interval) {
        measMan.updateMeasurementInterval(user, mId, interval);
     }
}
