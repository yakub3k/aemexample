package com.example.core.models;

import com.adobe.cq.wcm.core.components.models.Page;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ScriptVariable;

import javax.annotation.Resource;

@Model(adaptables = Resource.class)
@Exporter(name = "jackson", extensions = "json", selector = "model")
public class AlloModel {

    @ScriptVariable
    private Page currentPage;


}
