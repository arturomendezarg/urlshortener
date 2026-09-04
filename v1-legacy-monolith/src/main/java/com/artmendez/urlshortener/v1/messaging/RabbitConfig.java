package com.artmendez.urlshortener.v1.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion minima de RabbitMQ para V1.
 *
 * <p>Deliberadamente NO declara la cola {@code click-events} (sin bean {@code Queue} ni
 * {@code RabbitAdmin} explicito). Si V1 declarara la cola, Spring AMQP intentaria conectarse al
 * broker en el arranque del contexto ({@code ContextRefreshedEvent}) para declararla — lo cual
 * rompería todas las pruebas existentes que no levantan un contenedor de RabbitMQ (Postgres
 * solamente). En este diseño, el productor solo conoce el NOMBRE de la cola; declararla es
 * responsabilidad del consumidor (Analytics Worker, Tarea #7), que sí necesita que exista antes
 * de poder escuchar. La conexion de {@code RabbitTemplate} es perezosa: solo se abre en el
 * primer {@code convertAndSend}, nunca en el arranque.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
