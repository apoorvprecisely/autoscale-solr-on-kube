package com.apv.scale;

import com.apv.scale.exception.ResourceManagerException;

import java.io.IOException;

public interface ResourceManager {
    String allocateResource() throws IOException, InterruptedException, ResourceManagerException;
}
