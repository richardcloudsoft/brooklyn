package brooklyn.entity.nosql.mongodb;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.trait.Startable;

public class MongoDBClientImpl extends SoftwareProcessImpl implements MongoDBClient {
    
    @Override
    protected void connectSensors() {
        super.connectSensors();
        setAttribute(Startable.SERVICE_UP, true);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Class getDriverInterface() {
        return MongoDBClientDriver.class;
    }

    @Override
    public void runScript(String preStart, String scriptName) {
        ((MongoDBClientDriver)getDriver()).runScript(preStart, scriptName);
    }

}
