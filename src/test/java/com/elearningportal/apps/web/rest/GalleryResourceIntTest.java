package com.elearningportal.apps.web.rest;

import com.elearningportal.apps.ELearningApp;

import com.elearningportal.apps.domain.Gallery;
import com.elearningportal.apps.repository.GalleryRepository;
import com.elearningportal.apps.repository.search.GallerySearchRepository;
import com.elearningportal.apps.service.dto.GalleryDTO;
import com.elearningportal.apps.service.mapper.GalleryMapper;
import com.elearningportal.apps.web.rest.errors.ExceptionTranslator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.List;

import static com.elearningportal.apps.web.rest.TestUtil.createFormattingConversionService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test class for the GalleryResource REST controller.
 *
 * @see GalleryResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ELearningApp.class)
public class GalleryResourceIntTest {

    private static final String DEFAULT_IMAGE_URL = "AAAAAAAAAA";
    private static final String UPDATED_IMAGE_URL = "BBBBBBBBBB";

    @Autowired
    private GalleryRepository galleryRepository;

    @Autowired
    private GalleryMapper galleryMapper;

    @Autowired
    private GallerySearchRepository gallerySearchRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restGalleryMockMvc;

    private Gallery gallery;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final GalleryResource galleryResource = new GalleryResource(galleryRepository, galleryMapper, gallerySearchRepository);
        this.restGalleryMockMvc = MockMvcBuilders.standaloneSetup(galleryResource)
            .setCustomArgumentResolvers(pageableArgumentResolver)
            .setControllerAdvice(exceptionTranslator)
            .setConversionService(createFormattingConversionService())
            .setMessageConverters(jacksonMessageConverter).build();
    }

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Gallery createEntity(EntityManager em) {
        Gallery gallery = new Gallery()
            .imageUrl(DEFAULT_IMAGE_URL);
        return gallery;
    }

    @Before
    public void initTest() {
        gallerySearchRepository.deleteAll();
        gallery = createEntity(em);
    }

    @Test
    @Transactional
    public void createGallery() throws Exception {
        int databaseSizeBeforeCreate = galleryRepository.findAll().size();

        // Create the Gallery
        GalleryDTO galleryDTO = galleryMapper.toDto(gallery);
        restGalleryMockMvc.perform(post("/api/galleries")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(galleryDTO)))
            .andExpect(status().isCreated());

        // Validate the Gallery in the database
        List<Gallery> galleryList = galleryRepository.findAll();
        assertThat(galleryList).hasSize(databaseSizeBeforeCreate + 1);
        Gallery testGallery = galleryList.get(galleryList.size() - 1);
        assertThat(testGallery.getImageUrl()).isEqualTo(DEFAULT_IMAGE_URL);

        // Validate the Gallery in Elasticsearch
        Gallery galleryEs = gallerySearchRepository.findOne(testGallery.getId());
        assertThat(galleryEs).isEqualToIgnoringGivenFields(testGallery);
    }

    @Test
    @Transactional
    public void createGalleryWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = galleryRepository.findAll().size();

        // Create the Gallery with an existing ID
        gallery.setId(1L);
        GalleryDTO galleryDTO = galleryMapper.toDto(gallery);

        // An entity with an existing ID cannot be created, so this API call must fail
        restGalleryMockMvc.perform(post("/api/galleries")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(galleryDTO)))
            .andExpect(status().isBadRequest());

        // Validate the Gallery in the database
        List<Gallery> galleryList = galleryRepository.findAll();
        assertThat(galleryList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllGalleries() throws Exception {
        // Initialize the database
        galleryRepository.saveAndFlush(gallery);

        // Get all the galleryList
        restGalleryMockMvc.perform(get("/api/galleries?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(gallery.getId().intValue())))
            .andExpect(jsonPath("$.[*].imageUrl").value(hasItem(DEFAULT_IMAGE_URL.toString())));
    }

    @Test
    @Transactional
    public void getGallery() throws Exception {
        // Initialize the database
        galleryRepository.saveAndFlush(gallery);

        // Get the gallery
        restGalleryMockMvc.perform(get("/api/galleries/{id}", gallery.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(gallery.getId().intValue()))
            .andExpect(jsonPath("$.imageUrl").value(DEFAULT_IMAGE_URL.toString()));
    }

    @Test
    @Transactional
    public void getNonExistingGallery() throws Exception {
        // Get the gallery
        restGalleryMockMvc.perform(get("/api/galleries/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateGallery() throws Exception {
        // Initialize the database
        galleryRepository.saveAndFlush(gallery);
        gallerySearchRepository.save(gallery);
        int databaseSizeBeforeUpdate = galleryRepository.findAll().size();

        // Update the gallery
        Gallery updatedGallery = galleryRepository.findOne(gallery.getId());
        // Disconnect from session so that the updates on updatedGallery are not directly saved in db
        em.detach(updatedGallery);
        updatedGallery
            .imageUrl(UPDATED_IMAGE_URL);
        GalleryDTO galleryDTO = galleryMapper.toDto(updatedGallery);

        restGalleryMockMvc.perform(put("/api/galleries")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(galleryDTO)))
            .andExpect(status().isOk());

        // Validate the Gallery in the database
        List<Gallery> galleryList = galleryRepository.findAll();
        assertThat(galleryList).hasSize(databaseSizeBeforeUpdate);
        Gallery testGallery = galleryList.get(galleryList.size() - 1);
        assertThat(testGallery.getImageUrl()).isEqualTo(UPDATED_IMAGE_URL);

        // Validate the Gallery in Elasticsearch
        Gallery galleryEs = gallerySearchRepository.findOne(testGallery.getId());
        assertThat(galleryEs).isEqualToIgnoringGivenFields(testGallery);
    }

    @Test
    @Transactional
    public void updateNonExistingGallery() throws Exception {
        int databaseSizeBeforeUpdate = galleryRepository.findAll().size();

        // Create the Gallery
        GalleryDTO galleryDTO = galleryMapper.toDto(gallery);

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restGalleryMockMvc.perform(put("/api/galleries")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(galleryDTO)))
            .andExpect(status().isCreated());

        // Validate the Gallery in the database
        List<Gallery> galleryList = galleryRepository.findAll();
        assertThat(galleryList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteGallery() throws Exception {
        // Initialize the database
        galleryRepository.saveAndFlush(gallery);
        gallerySearchRepository.save(gallery);
        int databaseSizeBeforeDelete = galleryRepository.findAll().size();

        // Get the gallery
        restGalleryMockMvc.perform(delete("/api/galleries/{id}", gallery.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate Elasticsearch is empty
        boolean galleryExistsInEs = gallerySearchRepository.exists(gallery.getId());
        assertThat(galleryExistsInEs).isFalse();

        // Validate the database is empty
        List<Gallery> galleryList = galleryRepository.findAll();
        assertThat(galleryList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void searchGallery() throws Exception {
        // Initialize the database
        galleryRepository.saveAndFlush(gallery);
        gallerySearchRepository.save(gallery);

        // Search the gallery
        restGalleryMockMvc.perform(get("/api/_search/galleries?query=id:" + gallery.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(gallery.getId().intValue())))
            .andExpect(jsonPath("$.[*].imageUrl").value(hasItem(DEFAULT_IMAGE_URL.toString())));
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Gallery.class);
        Gallery gallery1 = new Gallery();
        gallery1.setId(1L);
        Gallery gallery2 = new Gallery();
        gallery2.setId(gallery1.getId());
        assertThat(gallery1).isEqualTo(gallery2);
        gallery2.setId(2L);
        assertThat(gallery1).isNotEqualTo(gallery2);
        gallery1.setId(null);
        assertThat(gallery1).isNotEqualTo(gallery2);
    }

    @Test
    @Transactional
    public void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(GalleryDTO.class);
        GalleryDTO galleryDTO1 = new GalleryDTO();
        galleryDTO1.setId(1L);
        GalleryDTO galleryDTO2 = new GalleryDTO();
        assertThat(galleryDTO1).isNotEqualTo(galleryDTO2);
        galleryDTO2.setId(galleryDTO1.getId());
        assertThat(galleryDTO1).isEqualTo(galleryDTO2);
        galleryDTO2.setId(2L);
        assertThat(galleryDTO1).isNotEqualTo(galleryDTO2);
        galleryDTO1.setId(null);
        assertThat(galleryDTO1).isNotEqualTo(galleryDTO2);
    }

    @Test
    @Transactional
    public void testEntityFromId() {
        assertThat(galleryMapper.fromId(42L).getId()).isEqualTo(42);
        assertThat(galleryMapper.fromId(null)).isNull();
    }
}
