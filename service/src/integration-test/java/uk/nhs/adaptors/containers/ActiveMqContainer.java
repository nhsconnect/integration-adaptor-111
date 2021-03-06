package uk.nhs.adaptors.containers;

import org.testcontainers.containers.GenericContainer;

public final class ActiveMqContainer extends GenericContainer<ActiveMqContainer> {

    public static final int ACTIVEMQ_PORT = 5672;
    private static ActiveMqContainer container;

    private ActiveMqContainer() {
        super("rmohr/activemq:5.15.9");
        addExposedPort(ACTIVEMQ_PORT);
    }

    public static ActiveMqContainer getInstance() {
        if (container == null) {
            container = new ActiveMqContainer();
        }
        return container;
    }

    @Override
    public void start() {
        super.start();
        var containerBrokerUri = "amqp://" + getContainerIpAddress() + ":" + getMappedPort(ACTIVEMQ_PORT);
        System.setProperty("PEM111_AMQP_BROKER", containerBrokerUri);
    }
}