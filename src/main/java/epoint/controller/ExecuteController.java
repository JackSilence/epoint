package epoint.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import epoint.service.IService;

@RestController
public class ExecuteController {
	@Autowired
	private ApplicationContext context;

	@GetMapping( value = "/execute/{name}" )
	public void execute( @PathVariable String name ) {
		Object bean = context.getBean( name );

		Assert.isInstanceOf( IService.class, bean );

		( ( IService ) bean ).exec();
	}
}