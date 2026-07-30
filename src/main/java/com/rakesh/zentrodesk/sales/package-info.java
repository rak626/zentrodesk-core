@ApplicationModule(allowedDependencies = {
        "common::api",
        "common::exception",
        "common::pagination",
        "common::search",
        "common::ownership",
        "platform::tenant",
        "platform::user",
        "platform::audit"
})
package com.rakesh.zentrodesk.sales;

import org.springframework.modulith.ApplicationModule;
