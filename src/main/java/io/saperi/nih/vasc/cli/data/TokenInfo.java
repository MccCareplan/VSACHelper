package io.saperi.nih.vasc.cli.data;

import lombok.Data;

import java.util.Date;

public @Data
class TokenInfo {
    private String apikey;
    private String tokenGrantingTicket;
    private Date tokenGrantedOn;

}
