package org.motechproject.server.osgi.ui;

import java.util.Collection;

public interface UIFrameworkService {

    void registerModule(ModuleRegistrationData module);

    void unregisterModule(String moduleName);

    Collection<ModuleRegistrationData> getRegisteredModules();

    ModuleRegistrationData getModuleData(String moduleName);
}
