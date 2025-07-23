package varahas.main.response;

import lombok.Data;

@Data
public class MlTokenResponse {
    
    public String access_token;
    public String token_type;
    public Integer expires_in;
    public String scope;
    public String user_id;
    public String refresh_token;

}
