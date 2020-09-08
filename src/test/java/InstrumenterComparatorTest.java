import static org.junit.Assert.*;

import org.junit.Test;

import de.rherzog.master.thesis.utils.InstrumenterComparator;

public class InstrumenterComparatorTest {

	@Test
	public void test() {
		InstrumenterComparator comparator = InstrumenterComparator.of("LSleep;.y([Ljava/lang/String;)V");
	}

}
