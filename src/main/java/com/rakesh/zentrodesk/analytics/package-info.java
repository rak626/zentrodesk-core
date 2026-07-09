@ApplicationModule(allowedDependencies = {
        "common::api",
        "platform::tenant",
        "sales::lead",
        "operations::booking",
        "operations::driver",
        "operations::task",
        "inventory::room",
        "communication::notification"
})
package com.rakesh.zentrodesk.analytics;

import org.springframework.modulith.ApplicationModule;
