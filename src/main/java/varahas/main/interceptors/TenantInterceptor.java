package varahas.main.interceptors;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import varahas.main.utils.TenantContext;

@Component
public class TenantInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException{
		String tenant = request.getHeader("X-Tenant-ID");
		if (tenant == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "X-Tenant-ID header faltante");
			return false;
		}
		TenantContext.setTenant(tenant);
		return true;
	}
	
	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) {
		TenantContext.clear();
	}
}
