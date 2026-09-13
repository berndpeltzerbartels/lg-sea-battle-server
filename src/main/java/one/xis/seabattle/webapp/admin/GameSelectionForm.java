package one.xis.seabattle.webapp.admin;

class GameSelectionForm {

    private static final String STANDARD_LANDSCAPE_ID = "__standard__";

    private String setupId;
    private String landscapeModelId;

    GameSelectionForm() {
    }

    GameSelectionForm(String setupId, String landscapeModelId) {
        this.setupId = setupId;
        this.landscapeModelId = landscapeModelId == null ? STANDARD_LANDSCAPE_ID : landscapeModelId;
    }

    public String getSetupId() {
        return setupId;
    }

    public void setSetupId(String setupId) {
        this.setupId = setupId;
    }

    public String getLandscapeModelId() {
        return landscapeModelId;
    }

    public void setLandscapeModelId(String landscapeModelId) {
        this.landscapeModelId = landscapeModelId;
    }

    String selectedLandscapeModelId() {
        return STANDARD_LANDSCAPE_ID.equals(landscapeModelId) ? null : landscapeModelId;
    }
}
