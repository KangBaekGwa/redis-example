package com.baekgwa.spring;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.baekgwa.spring.example.HashExample;
import com.baekgwa.spring.example.ListExample;
import com.baekgwa.spring.example.SetExample;
import com.baekgwa.spring.example.StringExample;
import com.baekgwa.spring.example.ZSetExample;

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
	private final ListExample listExample;
	private final SetExample setExample;
	private final HashExample hashExample;
	private final ZSetExample zSetExample;

	public ExampleRunner(StringExample stringExample, ListExample listExample, SetExample setExample, HashExample hashExample, ZSetExample zSetExample) {
		this.stringExample = stringExample;
		this.listExample = listExample;
		this.setExample = setExample;
		this.hashExample = hashExample;
		this.zSetExample = zSetExample;
	}

	@Override
	public void run(String... args) {
		// stringExample.run();
		// listExample.run();
		// setExample.run();
		// hashExample.run();
		zSetExample.run();
	}
}
