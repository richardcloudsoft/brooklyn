package brooklyn.entity.windows;

import brooklyn.entity.basic.SoftwareProcessImpl;

public class ChefApplicationImpl extends SoftwareProcessImpl implements ChefApplication {

    @Override
    public Class getDriverInterface() {
        return ChefApplicationDriver.class;
    }

}
