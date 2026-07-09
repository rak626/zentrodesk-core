@ApplicationModule(allowedDependencies = {
        "common::api",
        "common::exception",
        "common::pagination",
        "common::search",
        "platform::tenant",
        "platform::user"
})
package com.rakesh.zentrodesk.sales;

import org.springframework.modulith.ApplicationModule;
