/*
 * Copyright 2019
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukpinception.pdfeditor.pdfanno.model;

import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.SPAN_TYPE;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.linesOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.File;
import java.util.Arrays;
import java.util.Scanner;

import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.jcas.JCas;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.feature.FeatureSupportRegistryImpl;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.feature.PrimitiveUimaFeatureSupport;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.feature.SlotFeatureSupport;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer.ChainLayerSupport;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer.LayerBehaviorRegistryImpl;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer.LayerSupportRegistryImpl;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer.RelationLayerSupport;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer.SpanLayerSupport;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorStateImpl;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.PreRenderer;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.PreRendererImpl;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VDocument;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil;
import de.tudarmstadt.ukp.clarin.webanno.model.AnchoringMode;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.model.Mode;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.tcf.TcfReader;
import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.inception.pdfeditor.pdfanno.PdfAnnoRenderer;
import de.tudarmstadt.ukp.inception.pdfeditor.pdfanno.model.Offset;
import de.tudarmstadt.ukp.inception.pdfeditor.pdfanno.model.PdfAnnoModel;
import de.tudarmstadt.ukp.inception.pdfeditor.pdfanno.model.PdfExtractFile;

public class PdfAnnoRendererTest
{

    private @Mock AnnotationSchemaService schemaService;

    private Project project;
    private AnnotationLayer tokenLayer;
    private AnnotationFeature tokenPosFeature;
    private AnnotationLayer posLayer;
    private AnnotationFeature posFeature;

    private PreRenderer preRenderer;

    @Before
    public void setup()
    {
        initMocks(this);

        project = new Project();

        tokenLayer = new AnnotationLayer(Token.class.getName(), "Token", SPAN_TYPE, null, true,
            AnchoringMode.SINGLE_TOKEN);
        tokenLayer.setId(1l);

        tokenPosFeature = new AnnotationFeature();
        tokenPosFeature.setId(1l);
        tokenPosFeature.setName("pos");
        tokenPosFeature.setEnabled(true);
        tokenPosFeature.setType(POS.class.getName());
        tokenPosFeature.setUiName("pos");
        tokenPosFeature.setLayer(tokenLayer);
        tokenPosFeature.setProject(project);
        tokenPosFeature.setVisible(true);

        posLayer = new AnnotationLayer(POS.class.getName(), "POS", SPAN_TYPE, project, true,
            AnchoringMode.SINGLE_TOKEN);
        posLayer.setId(2l);
        posLayer.setAttachType(tokenLayer);
        posLayer.setAttachFeature(tokenPosFeature);

        posFeature = new AnnotationFeature();
        posFeature.setId(2l);
        posFeature.setName("PosValue");
        posFeature.setEnabled(true);
        posFeature.setType(CAS.TYPE_NAME_STRING);
        posFeature.setUiName("PosValue");
        posFeature.setLayer(posLayer);
        posFeature.setProject(project);
        posFeature.setVisible(true);

        FeatureSupportRegistryImpl featureSupportRegistry = new FeatureSupportRegistryImpl(
            asList(new PrimitiveUimaFeatureSupport(),
                new SlotFeatureSupport(schemaService)));
        featureSupportRegistry.init();

        LayerBehaviorRegistryImpl layerBehaviorRegistry = new LayerBehaviorRegistryImpl(asList());
        layerBehaviorRegistry.init();

        LayerSupportRegistryImpl layerRegistry = new LayerSupportRegistryImpl(asList(
            new SpanLayerSupport(featureSupportRegistry, null, schemaService,
                layerBehaviorRegistry),
            new RelationLayerSupport(featureSupportRegistry, null, schemaService,
                layerBehaviorRegistry),
            new ChainLayerSupport(featureSupportRegistry, null, schemaService,
                layerBehaviorRegistry)));
        layerRegistry.init();

        when(schemaService.listAnnotationLayer(any())).thenReturn(asList(posLayer));
        when(schemaService.listAnnotationFeature(any(AnnotationLayer.class)))
            .thenReturn(asList(posFeature));
        when(schemaService.getAdapter(any(AnnotationLayer.class))).then(_call -> {
            AnnotationLayer layer = _call.getArgument(0);
            return layerRegistry.getLayerSupport(layer).createAdapter(layer);
        });

        preRenderer = new PreRendererImpl(layerRegistry, schemaService);
    }

    @Test
    public void testRender() throws Exception
    {
        String file = "src/test/resources/tcf04-karin-wl.xml";
        String pdftxt = new Scanner(
            new File("src/test/resources/rendererTestPdfExtract.txt")).useDelimiter("\\Z").next();

        CAS cas = JCasFactory.createJCas().getCas();
        CollectionReader reader = CollectionReaderFactory.createReader(TcfReader.class,
            TcfReader.PARAM_SOURCE_LOCATION, file);
        reader.getNext(cas);
        JCas jCas = cas.getJCas();

        AnnotatorState state = new AnnotatorStateImpl(Mode.ANNOTATION);
        state.getPreferences().setWindowSize(10);
        state.setFirstVisibleUnit(WebAnnoCasUtil.getFirstSentence(jCas));
        state.setProject(project);

        VDocument vdoc = new VDocument();
        preRenderer.render(vdoc, 0, cas.getDocumentText().length(),
            jCas, schemaService.listAnnotationLayer(project));

        PdfExtractFile pdfExtractFile = new PdfExtractFile(pdftxt);
        PdfAnnoModel annoFile = PdfAnnoRenderer.render(state, vdoc,
            cas.getDocumentText(), schemaService, pdfExtractFile);

        assertThat(linesOf(new File("src/test/resources/rendererTestAnnoFile.anno"),
            "UTF-8")).isEqualTo(Arrays.asList(annoFile.getAnnoFileContent().split("\n")));
    }

    @Test
    public void tetsConvertToDocumentOffset() throws Exception
    {
        String file = "src/test/resources/tcf04-karin-wl.xml";
        String pdftxt = new Scanner(
            new File("src/test/resources/rendererTestPdfExtract.txt")).useDelimiter("\\Z").next();
        PdfExtractFile pdfExtractFile = new PdfExtractFile(pdftxt);

        CAS cas = JCasFactory.createJCas().getCas();
        CollectionReader reader = CollectionReaderFactory.createReader(TcfReader.class,
            TcfReader.PARAM_SOURCE_LOCATION, file);
        reader.getNext(cas);
        JCas jCas = cas.getJCas();

        AnnotatorState state = new AnnotatorStateImpl(Mode.ANNOTATION);
        state.getPreferences().setWindowSize(10);
        state.setFirstVisibleUnit(WebAnnoCasUtil.getFirstSentence(jCas));
        state.setProject(project);

        Offset docOffsetKarin = PdfAnnoRenderer
            .convertToDocumentOffset(cas.getDocumentText(), pdfExtractFile, new Offset(3, 7));
        assertThat(docOffsetKarin).isEqualTo(new Offset(0, 5));
        Offset docOffsetFliegt = PdfAnnoRenderer
            .convertToDocumentOffset(cas.getDocumentText(), pdfExtractFile, new Offset(8, 13));
        assertThat(docOffsetFliegt).isEqualTo(new Offset(6, 12));
        Offset docOffsetSie = PdfAnnoRenderer
            .convertToDocumentOffset(cas.getDocumentText(), pdfExtractFile, new Offset(28, 30));
        assertThat(docOffsetSie).isEqualTo(new Offset(29, 32));
        Offset docOffsetDort = PdfAnnoRenderer
            .convertToDocumentOffset(cas.getDocumentText(), pdfExtractFile, new Offset(35, 38));
        assertThat(docOffsetDort).isEqualTo(new Offset(38, 42));
    }
}
