package eu.daiad.web.security;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.daiad.web.model.Error;
import eu.daiad.web.model.RestResponse;
import eu.daiad.web.util.AjaxUtils;

public class CustomAccessDeniedHandler implements AccessDeniedHandler {

	private String errorPage;

	public CustomAccessDeniedHandler(String errorPage) {
		this.errorPage = errorPage;
	}

	protected static final Log logger = LogFactory
			.getLog(CustomAccessDeniedHandler.class);

	public void handle(HttpServletRequest request,
			HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException,
			ServletException {
		if (!response.isCommitted()) {
			if (errorPage != null) {
				if (AjaxUtils.isAjaxRequest(request)) {
					response.setContentType("application/json;charset=UTF-8");
					response.setHeader("Cache-Control", "no-cache");

					ObjectMapper mapper = new ObjectMapper();

					if (request.getRequestURI().equals("/logout")) {
						response.setStatus(HttpStatus.OK.value());

						response.getWriter().print(
								mapper.writeValueAsString(new RestResponse()));
					} else {
						response.setStatus(HttpStatus.FORBIDDEN.value());

						response.getWriter().print(
								mapper.writeValueAsString(new RestResponse(
										Error.ERROR_AUTH_FAILED,
										"Authentication has failed.")));
					}
				} else {
					// Put exception into request scope (perhaps of use to a
					// view)
					request.setAttribute(WebAttributes.ACCESS_DENIED_403,
							accessDeniedException);

					// Set the 403 status code.
					response.setStatus(HttpServletResponse.SC_FORBIDDEN);

					// forward to error page.
					RequestDispatcher dispatcher = request
							.getRequestDispatcher(errorPage);
					dispatcher.forward(request, response);
				}
			} else {
				response.sendError(HttpServletResponse.SC_FORBIDDEN,
						accessDeniedException.getMessage());
			}
		}
	}

	public void setErrorPage(String errorPage) {
		if ((errorPage != null) && !errorPage.startsWith("/")) {
			throw new IllegalArgumentException("errorPage must begin with '/'");
		}

		this.errorPage = errorPage;
	}

}