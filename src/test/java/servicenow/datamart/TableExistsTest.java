package servicenow.datamart;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;

import servicenow.api.Table;
import servicenow.api.TestingManager;
import servicenow.api.TestingProfile;

/**
 * Inactive because the tableExists method is tested in CreateTableTest
 */
@RunWith(Parameterized.class)
public class TableExistsTest {

	@Parameters(name = "{index}:{0}")
	public static TestingProfile[] profiles() {
		return TestingManager.getDatamartProfiles();
	}
		
	static Logger logger = TestingManager.getLogger(DBUtil.class);

	public TableExistsTest(TestingProfile profile) throws Exception {
		TestingManager.setProfile(this.getClass(), profile);
	}
	
	@Test
	public void testTableExistsTrue() throws Exception {
		TestingManager.bannerStart(this.getClass(), "testTableExistsTrue");
		// logger.info("testTableExistsTrue");
		Database db = ResourceManager.getDatabase();
		String tablename = "core_company";		
		Table table = ResourceManager.getSession().table(tablename);
		assertNotNull(db);
		db.createMissingTable(table, tablename);
		assertTrue(db.tableExists(tablename));
		assertFalse(db.tableExists(tablename.toUpperCase()));
	}
	
	@Test
	public void testTableExistsFalse() throws Exception {
		TestingManager.bannerStart(this.getClass(), "testTableExistsFalse");
		// logger.info("testTableExistsFalse");
		Database db = ResourceManager.getDatabase();
		String tablename = "some_nonexistent_table";
		assertNotNull(db);
		assertFalse(db.tableExists(tablename));
		assertFalse(db.tableExists(tablename.toUpperCase()));
	}
	
}