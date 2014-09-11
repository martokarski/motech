package org.motechproject.mds.web.it;

import org.apache.commons.beanutils.MethodUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.motechproject.mds.dto.EntityDto;
import org.motechproject.mds.dto.FieldBasicDto;
import org.motechproject.mds.dto.FieldDto;
import org.motechproject.mds.dto.LookupDto;
import org.motechproject.mds.dto.LookupFieldDto;
import org.motechproject.mds.dto.RestOptionsDto;
import org.motechproject.mds.dto.TypeDto;
import org.motechproject.mds.query.QueryParams;
import org.motechproject.mds.service.EntityService;
import org.motechproject.mds.service.JarGeneratorService;
import org.motechproject.mds.service.MotechDataService;
import org.motechproject.mds.util.ClassName;
import org.motechproject.mds.util.Order;
import org.motechproject.mds.util.PropertyUtil;
import org.motechproject.mds.util.ServiceUtil;
import org.motechproject.testing.osgi.BasePaxIT;
import org.motechproject.testing.osgi.TestContext;
import org.motechproject.testing.osgi.container.MotechNativeTestContainerFactory;
import org.ops4j.pax.exam.ExamFactory;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
@ExamFactory(MotechNativeTestContainerFactory.class)
public class MdsRestBundleIT extends BasePaxIT {

    private static final String ENTITY_NAME = "RestTestEnt";
    private static final String ENTITY_URL = String.format("http://localhost:%d/mds/rest/%s",
            TestContext.getJettyPort(), ENTITY_NAME);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Inject
    private EntityService entityService;

    @Inject
    private JarGeneratorService jarGeneratorService;

    @Inject
    private BundleContext bundleContext;

    @Override
    protected Collection<String> getAdditionalTestDependencies() {
        return asList("org.motechproject:motech-scheduler");
    }

    @BeforeClass
    public static void setUpClass() throws IOException, InterruptedException {
        createAdminUser();
        login();
    }

    @Before
    public void setUp() throws IOException {
        clearEntities();
        prepareEntity();
        jarGeneratorService.regenerateMdsDataBundle(true);

        MotechDataService dataService = ServiceUtil.getServiceForInterfaceName(bundleContext,
                ClassName.getInterfaceName(ENTITY_NAME));
        dataService.deleteAll();
    }

    @Test
    public void testBasicCrud() throws Exception {
        MotechDataService dataService = ServiceUtil.getServiceForInterfaceName(bundleContext,
                ClassName.getInterfaceName(ENTITY_NAME));
        final Class<?> entityClass = dataService.getClassType();

        // CREATE
        // create 11 records using REST

        getLogger().info("Creating instance via REST");

        for (int i = 0; i < 11; i++) {
            HttpPost post = new HttpPost(ENTITY_URL);
            post.setEntity(new StringEntity(recordJsonString("string" + i, i),
                    ContentType.APPLICATION_JSON));

            HttpResponse response = getHttpClient().execute(post);

            assertNotNull(response);
            assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        }

        // UPDATE
        // update records 4 & 5
        // we add "updated" at the end of the string

        // GET object Ids
        // TODO: should we return ID in REST responses?
        // TODO: invoke this lookup through REST
        QueryParams queryParams = new QueryParams(new Order("intField", Order.Direction.ASC));
        List objList = (List) MethodUtils.invokeMethod(dataService, "byIntSet", new Object[] {
                new HashSet<>(asList(4, 5)), queryParams
        });
        // verify that the lookup works correctly just in case
        assertNotNull(objList);
        assertEquals(2, objList.size());

        for (Object record : objList) {
            HttpPut put = new HttpPut(ENTITY_URL);
            put.setEntity(new StringEntity(recordJsonString(
                    PropertyUtil.getProperty(record, "strField") + "Updated",
                    (int) PropertyUtil.getProperty(record, "intField"),
                    (long) PropertyUtil.getProperty(record, "id")
            )));

            HttpResponse response = getHttpClient().execute(put);

            assertNotNull(response);
            assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        }

        // READ
        // read records from 5 to 1

        getLogger().info("Reading instances via REST");

        String body = getHttpClient().get(ENTITY_URL + "?page=2&pageSize=5&sort=intField&order=desc",
                new BasicResponseHandler());

        assertNotNull(body);

        List list = OBJECT_MAPPER.readValue(body, OBJECT_MAPPER.getTypeFactory()
                .constructCollectionType(List.class, entityClass));

        assertEquals(5, list.size());
        for (int i = 0; i < 5; i++) {
            int expectedIntField = 5 - i;
            // we updated the int fields X 10 for these two records
            boolean fieldWasUpdated = expectedIntField == 5 || expectedIntField == 4;
            // and added "Updated" to the string field
            String expectedStrField = "string" + expectedIntField;
            if (fieldWasUpdated) {
                expectedStrField += "Updated";
            }

            Object record = list.get(i);

            assertEquals(expectedStrField, PropertyUtils.getProperty(record, "strField"));
            assertEquals(expectedIntField, PropertyUtils.getProperty(record, "intField"));
        }

        for (int i = 0; i < list.size(); i++) {
            Object rec1 = list.get(i);
            String responseBody = getHttpClient().get(ENTITY_URL + "?id=" + PropertyUtils.getProperty(rec1, "id"),
                    new BasicResponseHandler());

            assertNotNull(responseBody);

            Object rec2 = OBJECT_MAPPER.readValue(responseBody, OBJECT_MAPPER.getTypeFactory().constructType(entityClass));

            assertEquals(PropertyUtils.getProperty(rec2, "strField"), PropertyUtils.getProperty(rec1, "strField"));
        }

        // DELETE
        // delete 5 records using REST

        getLogger().info("Delete instances via REST");

        List list2 = dataService.retrieveAll();
        for (int i = 0; i < 5; i++) {
            Object record2 = list2.get(i);
            long id = (long) PropertyUtils.getProperty(record2,"id");

            HttpDelete delete = new HttpDelete(ENTITY_URL + "/" + id);
            HttpResponse response2 = getHttpClient().execute(delete);

            assertNotNull(response2);
            assertEquals(HttpStatus.SC_OK, response2.getStatusLine().getStatusCode());

            assertNull(dataService.findById(id));
        }
        assertEquals(dataService.retrieveAll().size(), 6);
    }

    private void clearEntities() {
        getLogger().info("Cleaning up entities");

        for (EntityDto entity : entityService.listEntities()) {
            if (!entity.isDDE()) {
                entityService.deleteEntity(entity.getId());
            }
        }
    }

    private void prepareEntity() throws IOException {
        EntityDto entityDto = new EntityDto(ENTITY_NAME);
        entityDto = entityService.createEntity(entityDto);
        FieldDto strField = new FieldDto(null, entityDto.getId(), TypeDto.STRING,
                new FieldBasicDto("strFieldDisp", "strField"), false, null);
        FieldDto intField = new FieldDto(null, entityDto.getId(), TypeDto.INTEGER,
                new FieldBasicDto("intFieldDisp", "intField"), false, null);

        entityService.addFields(entityDto, asList(strField, intField));

        RestOptionsDto restOptions = new RestOptionsDto(true, true, true, true);

        entityService.updateRestOptions(entityDto.getId(), restOptions);

        // a set based lookup for our convenience
        LookupFieldDto intLookupField = new LookupFieldDto(null, "intField", LookupFieldDto.Type.SET);
        LookupDto lookup = new LookupDto("byIntSet", false, true, asList(intLookupField),false);
        entityService.addLookups(entityDto.getId(), asList(lookup));
    }

    private String recordJsonString(String strField, int intField) {
        return String.format("{\"strField\": \"%s\", \"intField\": \"%d\"}",
                strField, intField);
    }

    private String recordJsonString(String strField, int intField, Long id) {
        return String.format("{\"strField\": \"%s\", \"intField\": \"%d\", \"id\": \"%d\"}",
                strField, intField, id);

    }
}
