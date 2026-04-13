package kafka.kafkaService.email.application.port.out;

public interface MessagePullPort {

    int pullAndProcess(EventProcessor processor);

}