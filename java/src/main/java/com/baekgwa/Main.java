package com.baekgwa;

import com.baekgwa.example.HashExample;
import com.baekgwa.example.ListExample;
import com.baekgwa.example.PipeliningExample;
import com.baekgwa.example.PubSubExample;
import com.baekgwa.example.SetExample;
import com.baekgwa.example.StringExample;
import com.baekgwa.example.TransactionExample;
import com.baekgwa.example.ZSetExample;

public class Main {

	public static void main(String[] args) throws Exception {

		// 1. String 예제
		// StringExample.start();

		// 2. List 예제
		// ListExample.start();

		// 3. Set 예제
		// SetExample.start();

		// 4. Hash 예제
		// HashExample.start();

		// 5. ZSet 예제
		// ZSetExample.start();

		// 6. Pub/Sub 예제
		// PubSubExample.start();

		// 7. Transaction 예제
		// TransactionExample.start();

		// 8. Pipelining 예제
		PipeliningExample.start();
	}
}