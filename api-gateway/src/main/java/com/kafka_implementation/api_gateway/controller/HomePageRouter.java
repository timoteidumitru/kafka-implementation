package com.kafka_implementation.api_gateway.controller;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@Configuration
public class HomePageRouter {

    @Bean
    public RouterFunction<ServerResponse> homepageRouter() {
        return route(GET("/"), request -> ServerResponse.ok().render("home"));
    }
}

