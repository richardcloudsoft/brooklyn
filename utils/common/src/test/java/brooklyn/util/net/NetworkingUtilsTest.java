package brooklyn.util.net;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.test.Asserts;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.net.Networking;
import brooklyn.util.text.Identifiers;
import brooklyn.util.time.Time;

public class NetworkingUtilsTest {

    private static final Logger log = LoggerFactory.getLogger(NetworkingUtilsTest.class);
    
    @Test
    public void testValidIp() throws Exception {
        assertTrue(Networking.isValidIp4("127.0.0.1"));
        assertTrue(Networking.isValidIp4("0.0.0.0"));
        assertFalse(Networking.isValidIp4("foo"));
        assertTrue(Networking.isValidIp4(Networking.LOOPBACK.getHostName()));
        assertTrue(Networking.isValidIp4("0.0.0.00"));
        assertTrue(Networking.isValidIp4("127.0.0.000001"));
        assertFalse(Networking.isValidIp4("127.0.0.256"));
        assertFalse(Networking.isValidIp4("127.0.0."));
        assertFalse(Networking.isValidIp4("127.0.0.9f"));
        assertFalse(Networking.isValidIp4("127.0.0.1."));
    }
        
    @Test
    public void testGetInetAddressWithFixedNameByIpBytes() throws Exception {
        InetAddress addr = Networking.getInetAddressWithFixedName(new byte[] {1,2,3,4});
        assertEquals(addr.getAddress(), new byte[] {1,2,3,4});
        assertEquals(addr.getHostName(), "1.2.3.4");
    }
    
    @Test
    public void testGetInetAddressWithFixedNameByIp() throws Exception {
        InetAddress addr = Networking.getInetAddressWithFixedName("1.2.3.4");
        assertEquals(addr.getAddress(), new byte[] {1,2,3,4});
        assertEquals(addr.getHostName(), "1.2.3.4");
        
        InetAddress addr2 = Networking.getInetAddressWithFixedName("255.255.255.255");
        assertEquals(addr2.getAddress(), new byte[] {(byte)(int)255,(byte)(int)255,(byte)(int)255,(byte)(int)255});
        assertEquals(addr2.getHostName(), "255.255.255.255");
        
        InetAddress addr3 = Networking.getInetAddressWithFixedName("localhost");
        assertEquals(addr3.getHostName(), "localhost");
        
    }
    
    @Test(groups="Integration")
    public void testGetInetAddressWithFixedNameButInvalidIpThrowsException() throws Exception {
        // as with ByonLocationResolverTest.testNiceError
        // some DNS servers give an IP for this "hostname"
        // so test is marked as integration now
        try {
            Networking.getInetAddressWithFixedName("1.2.3.400");
            fail();
        } catch (Exception e) {
            if (Exceptions.getFirstThrowableOfType(e, UnknownHostException.class) == null) throw e;
        }
    }
    
    @Test(groups="Integration")
    public void testIsPortAvailableReportsTrueWhenPortIsFree() throws Exception {
        int port = 58769;
        int numFree = 0;
        for (int i = 0; i < 10; i++) {
            if (Networking.isPortAvailable(port))
                numFree++;
        }
        if (numFree<=5)
            fail("This test requires that at least some ports near 58769+ not be in use.");
    }
    
    @Test
    public void testIsPortAvailableReportsFalseWhenPortIsInUse() throws Exception {
        int port = 58767;
        ServerSocket ss = null;
        do {
            port ++;
            if (Networking.isPortAvailable(port)) {
                try {
                    ss = new ServerSocket(port);
                    log.info("acquired port on "+port+" for test "+JavaClassNames.niceClassAndMethod());
                    assertFalse(Networking.isPortAvailable(port), "port mistakenly reported as available");
                } finally {
                    if (ss != null) {
                        ss.close();
                    }
                }
            }
            // repeat until we can get a port
        } while (ss == null && port < 60000);
        Assert.assertNotNull(ss, "could not get a port");
        
        final int portF = port;
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertTrue(Networking.isPortAvailable(portF), "port "+portF+" not made available afterwards");
            }});
    }
    
    //just some system health-checks... localhost may not resolve properly elsewhere
    //(e.g. in geobytes, AddressableLocation, etc) if this does not work
    
    @Test
    public void testLocalhostIpLookup() throws UnknownHostException {
        InetAddress address = InetAddress.getByName("127.0.0.1");
        Assert.assertEquals(127, address.getAddress()[0]);
        Assert.assertTrue(Networking.isPrivateSubnet(address));
    }
    
    @Test
    public void testLocalhostLookup() throws UnknownHostException {
        InetAddress address = InetAddress.getByName("localhost");
        Assert.assertEquals(127, address.getAddress()[0]);
        Assert.assertTrue(Networking.isPrivateSubnet(address));
        Assert.assertEquals("127.0.0.1", address.getHostAddress());
    }

    @Test
    public void test10_x_x_xSubnetPrivate() throws UnknownHostException {
        InetAddress address = InetAddress.getByAddress(new byte[] { 10, 0, 0, 1 });
        Assert.assertTrue(Networking.isPrivateSubnet(address));
    }

    @Test
    public void test172_16_x_xSubnetPrivate() throws UnknownHostException {
        InetAddress address = InetAddress.getByAddress(new byte[] { (byte)172, 31, (byte)255, (byte)255 });
        Assert.assertTrue(Networking.isPrivateSubnet(address));
    }

    @Test(groups="Integration")
    public void testBogusHostnameUnresolvable() {
        Assert.assertEquals(Networking.resolve("bogus-hostname-"+Identifiers.makeRandomId(8)), null);
    }

}
