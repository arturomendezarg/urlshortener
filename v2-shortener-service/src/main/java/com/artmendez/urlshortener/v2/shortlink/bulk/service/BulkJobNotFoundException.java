package com.artmendez.urlshortener.v2.shortlink.bulk.service;

/**
 * No bulk job exists for the requested id, OR it exists but belongs to a different owner.
 * Deliberately the same exception (and the same {@code 404}) for both cases: telling a
 * non-owner "403, this job exists" would confirm a jobId is real to someone who has no business
 * knowing that.
 */
public class BulkJobNotFoundException extends RuntimeException {

    public BulkJobNotFoundException(Long jobId) {
        super("No bulk job found for id '" + jobId + "'");
    }
}
