@ApplicationModule(allowedDependencies = {
        "common::api",
        "common::exception",
        "common::pagination",
        "common::search",
        "platform::tenant",
        "platform::user",
        "sales::customer",
        "sales::lead",
        "sales::quotation",
        "platform::audit"
})
package com.rakesh.zentrodesk.operations;

import org.springframework.modulith.ApplicationModule;
