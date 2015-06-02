package org.hyperic.hq.ui.action.portlet.savedqueries;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts2.ServletActionContext;
import org.apache.tiles.Attribute;
import org.apache.tiles.AttributeContext;
import org.apache.tiles.context.TilesRequestContext;
import org.apache.tiles.preparer.ViewPreparer;
import org.hyperic.hq.bizapp.shared.AppdefBoss;
import org.hyperic.hq.bizapp.shared.AuthzBoss;
import org.hyperic.hq.ui.Constants;
import org.hyperic.hq.ui.WebUser;
import org.hyperic.hq.ui.action.BaseActionNG;
import org.hyperic.hq.ui.server.session.DashboardConfig;
import org.hyperic.hq.ui.shared.DashboardManager;
import org.hyperic.hq.ui.util.CheckPermissionsUtil;
import org.hyperic.hq.ui.util.RequestUtils;
import org.hyperic.hq.ui.util.SessionUtils;
import org.hyperic.util.StringUtil;
import org.hyperic.util.config.ConfigResponse;
import org.hyperic.util.timer.StopWatch;
import org.springframework.stereotype.Component;


@Component("savedqueriesViewActionNG")
public class ViewActionNG extends BaseActionNG implements ViewPreparer {
	private final Log log = LogFactory.getLog(ViewActionNG.class);
	
	@Resource
    private AuthzBoss authzBoss;
	@Resource
    private AppdefBoss appdefBoss;
	@Resource
    private DashboardManager dashboardManager;
    private final Log timingLog = LogFactory.getLog("DASHBOARD-TIMING");

    public void execute(TilesRequestContext reqContext, AttributeContext attrContext) {
		StopWatch timer = new StopWatch();
		HttpServletRequest request = ServletActionContext.getRequest();
		
        HttpSession session = request.getSession();

        WebUser user = SessionUtils.getWebUser(session);
        DashboardConfig dashConfig = dashboardManager.findDashboard((Integer) session
            .getAttribute(Constants.SELECTED_DASHBOARD_ID), user, authzBoss);
        ConfigResponse dashPrefs = dashConfig.getConfig();

        // get all the displayed subtypes

        List<String> chartList = null;

        try {
            chartList = StringUtil.explode(dashPrefs.getValue(Constants.USER_DASHBOARD_CHARTS),
                Constants.DASHBOARD_DELIMITER);
        } catch (RuntimeException e) {

        }

        if (chartList != null) {
            List<KeyValuePair> charts = new ArrayList<KeyValuePair>();

            for (String chartListStr : chartList) {

                List<String> chart = StringUtil.explode(chartListStr, ",");

                // the saved chart preference should have exactly two
                // elements: the name of the chart and the URL... so there
                // are things that can break stuff: commas or pipes in the name
                // of the chart -- these will be encoded in the action that
                // saves
                // the preference but just to be safe, well defend against
                // bogosity

                // if something bjorked the preference stringification
                // scheme, we can't display diddly squat about the preference
                if (chart.size() != 2) {
                    // it's amazing but true: bogosity has been found
                    if (log.isTraceEnabled()) {
                        log.trace("chart preference not understood: " + chart);
                    }
                    continue;
                }

                // Determine what entityIds can be viewed by this user
                // This code probably should be in the boss somewhere but
                // for now doing it here...
                try {
                
	                if (CheckPermissionsUtil.canUserViewChart(RequestUtils.getSessionId(request).intValue(), chart.get(1),
	                    appdefBoss)) {
	                    Iterator<String> j = chart.iterator();
	                    String name = j.next();
	                    String url = j.next();
	                    // the name might be generated by user input, we need to
	                    // make
	                    // sure
	                    // their delimiters' presence in the names are deserialized
	                    // from
	                    // the
	                    // preference system
	                    name = StringUtil.replace(name, "&#124;", Constants.DASHBOARD_DELIMITER);
	                    name = StringUtil.replace(name, "&#44;", ",");
	
	                    charts.add(new KeyValuePair(name, url));
	                }
                } catch (Exception ex) {
                	log.error(ex);
                }
            }

            reqContext.getRequestScope().put("charts",  charts );
        } else {
        	reqContext.getRequestScope().put("charts", new ArrayList<KeyValuePair>() );
        }
        timingLog.trace("SavedQueries - timing [" + timer.toString() + "]");		
	}

}
