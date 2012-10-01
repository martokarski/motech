package org.motechproject.server.ui;

import org.joda.time.Duration;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.motechproject.server.osgi.ui.ModuleRegistrationData;
import org.motechproject.server.osgi.ui.UIFrameworkService;
import org.motechproject.server.startup.MotechPlatformState;
import org.motechproject.server.startup.StartupManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Collection;

@Controller
public class DashboardController {

    private StartupManager startupManager = StartupManager.getInstance();

    @Autowired
    private UIFrameworkService uiFrameworkService;

    @RequestMapping({"/index", "/", "/home"})
    public ModelAndView index(@RequestParam(required = false) String moduleName) {

        ModelAndView mav;

        // check if this is the first run
        if (startupManager.getPlatformState() == MotechPlatformState.NEED_CONFIG) {
            mav = new ModelAndView("redirect:startup.do");
        } else {
            mav = new ModelAndView("index");

            mav.addObject("uptime", getUptime());

            Collection<ModuleRegistrationData> modules = uiFrameworkService.getRegisteredModules();
            mav.addObject("modules", modules);

            if (moduleName != null) {
                ModuleRegistrationData currentModule = uiFrameworkService.getModuleData(moduleName);
                if (currentModule != null) {
                    mav.addObject("currentModule", currentModule);
                }
            }
        }

        return mav;
    }

    @RequestMapping("/old")
    public ModelAndView oldIndex() {
        return new ModelAndView("oldindex");
    }

    private String getUptime() {
        RuntimeMXBean mx = ManagementFactory.getRuntimeMXBean();
        Period uptime = new Duration(mx.getUptime()).toPeriod();

        PeriodFormatter formatter = new PeriodFormatterBuilder()
                .appendDays()
                .appendSuffix(" day", " days")
                .appendSeparator(" and ")
                .appendMinutes()
                .appendSuffix(" minute", " minutes")
                .toFormatter();

        return formatter.print(uptime.normalizedStandard());
    }
}
