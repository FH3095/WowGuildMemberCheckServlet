package eu._4fh.guildsync.rest.providers;

import java.lang.reflect.Parameter;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;

import eu._4fh.guildsync.rest.helper.RequiredParam;

@Provider
public class RequiredParameterFilter implements ContainerRequestFilter {
	@Context
	private ResourceInfo resourceInfo;

	@Override
	public void filter(ContainerRequestContext requestContext) {
		for (Parameter parameter : resourceInfo.getResourceMethod().getParameters()) {
			if (parameter.isAnnotationPresent(QueryParam.class) && parameter.isAnnotationPresent(RequiredParam.class)) {
				QueryParam queryAnnotation = parameter.getAnnotation(QueryParam.class);
				if (!requestContext.getUriInfo().getQueryParameters().containsKey(queryAnnotation.value())) {
					throw new BadRequestException("Missing required query parameter: " + queryAnnotation.value());
				}
			}
		}
	}
}
