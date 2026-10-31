package dev.sleep.optsupport;

/** lab.opt.JobSiteIndex: a brain no longer used (its entity removed, the brain replaced) gives back its job site. */
public interface JobSiteBrainAccess {
    void probe$release();

    /** re-reads the JOB_SITE memory into the index (a copy filled directly) */
    void probe$follow();
}
