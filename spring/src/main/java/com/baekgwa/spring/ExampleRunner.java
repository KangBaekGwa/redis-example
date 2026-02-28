package com.baekgwa.spring;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.baekgwa.spring.example.StringExample;

/**
 * PackageName : com.baekgwa.spring
 * FileName    : ExampleRunner
 * Author      : Baekgwa
 * Date        : 26. 2. 28.
 * Description : 
 * =====================================================================================================================
 * DATE          AUTHOR               NOTE
 * ---------------------------------------------------------------------------------------------------------------------
 * 2025-08-03     Baekgwa               Initial creation
 **/
@Component
public class ExampleRunner implements CommandLineRunner {

	private final StringExample stringExample;

	public ExampleRunner(StringExample stringExample) {
		this.stringExample = stringExample;
	}

	@Override
	public void run(String... args) {
		stringExample.run();
	}
}
