package org.cloudfoundry.identity.uaa.login.util;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class FakeJavaMailSenderTest {

    FakeJavaMailSender sender = null;

    @Before
    public void createSender() {
        sender = new FakeJavaMailSender();
    }


    @Test
    public void testCreateMimeMessage() throws Exception {
        assertNotNull(sender.createMimeMessage());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCreateMimeMessage1() throws Exception {
        sender.createMimeMessage(null);
    }

    @Test
    public void testSend() throws Exception {
        sender.send(sender.createMimeMessage());
        assertEquals(1, sender.getSentMessages().size());
    }

    @Test
    public void testNoMemoryLeak() throws Exception {
        for (int i=0; i<(sender.maxNumberOfMessage*2); i++) {
            sender.send(sender.createMimeMessage());
        }
        assertEquals(sender.maxNumberOfMessage, sender.getSentMessages().size());
    }

    @Test
    public void testClearMessages() throws Exception {
        testNoMemoryLeak();
        sender.getSentMessages().clear();
        assertEquals(0, sender.getSentMessages().size());
    }


}