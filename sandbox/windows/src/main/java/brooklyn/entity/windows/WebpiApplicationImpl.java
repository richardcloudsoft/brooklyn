package brooklyn.entity.windows;

import brooklyn.entity.basic.SoftwareProcessImpl;

public class WebpiApplicationImpl extends SoftwareProcessImpl implements WebpiApplication {

    @Override
    public Class getDriverInterface() {
        return WebpiApplicationDriver.class;
    }

}
