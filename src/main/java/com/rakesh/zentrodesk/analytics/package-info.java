@ApplicationModule(allowedDependencies = {
        "common::api",
        "common::exception",
        "iam::principal",
        "platform::tenant",
        "platform::audit",
        "sales::customer",
        "sales::lead",
        "sales::quotation",
        "operations::booking",
        "operations::driver",
        "operations::task",
        "inventory::room",
        "communication::notification"
})
package com.rakesh.zentrodesk.analytics;

import org.springframework.modulith.ApplicationModule;
