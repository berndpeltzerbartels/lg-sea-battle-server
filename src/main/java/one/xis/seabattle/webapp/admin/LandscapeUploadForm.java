package one.xis.seabattle.webapp.admin;

import one.xis.Upload;
import one.xis.UploadedFile;

class LandscapeUploadForm {

    @Upload(maxSize = 2_000_000)
    private UploadedFile landscapeFile;

    public UploadedFile getLandscapeFile() {
        return landscapeFile;
    }
}
