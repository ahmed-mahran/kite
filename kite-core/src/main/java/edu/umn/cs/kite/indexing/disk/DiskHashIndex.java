package edu.umn.cs.kite.indexing.disk;

import edu.umn.cs.kite.common.DebugFlagger;
import edu.umn.cs.kite.common.KiteInstance;
import edu.umn.cs.kite.indexing.memory.MemoryHashIndexSegment;
import edu.umn.cs.kite.indexing.memory.MemoryIndexSegment;
import edu.umn.cs.kite.querying.Query;
import edu.umn.cs.kite.streaming.StreamDataset;
import edu.umn.cs.kite.util.ConstantsAndDefaults;
import edu.umn.cs.kite.util.KiteUtils;
import edu.umn.cs.kite.util.TemporalPeriod;
import edu.umn.cs.kite.util.microblogs.Microblog;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by amr_000 on 9/3/2016.
 */
public class DiskHashIndex extends DiskIndex<String,Microblog> {

    public DiskHashIndex(String indexUniqueName, StreamDataset stream,
                         boolean load) {
        this.indexUniqueName = indexUniqueName;
        this.stream = stream;

        if(load)
            loadSegments();
    }

    @Override
    public boolean addSegment(MemoryIndexSegment memorySegment, TemporalPeriod
            p) {
        DiskHashIndexSegment segment = null;
        try {
            segment = new DiskHashIndexSegment(this.
                    getIndexUniqueName(), this.
                    getIndexUniqueName()+"_seg"+KiteUtils.int2str
                    (indexSegments.size(),4),
                    (MemoryHashIndexSegment) memorySegment, stream);
            indexSegments.add(segment);
            indexSegmentsTime.add(p);
            appendToDisk(p);
            //TODO: check here actual segments with dir in memory
            if(indexSegments.size() > ConstantsAndDefaults
                    .NUM_DISK_SEGMENTS_IN_MEMORY_DIRECTORY) {
                int ind = indexSegments.size()-ConstantsAndDefaults
                        .NUM_DISK_SEGMENTS_IN_MEMORY_DIRECTORY-1;
                indexSegments.get(ind).discardInMemoryDirectory();
            }
        } catch (IOException e) {
            String errMsg = "Unable to flush contents of memory index " +
                    ""+memorySegment.getName()+" to HDFS.";
            errMsg += System.lineSeparator();
            errMsg += "Error: "+e.getMessage();
            KiteInstance.logError(errMsg);
            System.err.println(errMsg);
            return false;
        }
        return true;
    }

    @Override
    public ArrayList<Microblog> search (String key, int k,
                                        TemporalPeriod period, Query q) {
        int startOverlapPeriod = KiteUtils.binarySearch_StartOverlap(
                indexSegmentsTime, period);

        if(startOverlapPeriod >= 0) {
            ArrayList<Microblog> results = new ArrayList<>();
            int remainingAnswerSize = k;
            for(int i = startOverlapPeriod; i < indexSegmentsTime.size() &&
                    indexSegmentsTime.get(i).overlap(period) && results.size()<k
                    ; ++i) {
                ArrayList<Microblog> segResults = indexSegments.get(i).search
                        (key, remainingAnswerSize, q);
                if(segResults != null) {
                    results.addAll(segResults);
                    remainingAnswerSize -= segResults.size();
                }
            }
            return results;
        }
        else //no results as search temporal period is out of index time horizon
            return new ArrayList<>();
    }

    @Override
    public void destroy() {
        for(DiskIndexSegment segment: indexSegments)
            segment.destroy();
    }

    @Override
    protected DiskIndexSegment loadNextSegment() {
        DiskHashIndexSegment segment = null;
        try {
            segment = new DiskHashIndexSegment(this.
                    getIndexUniqueName(), this.
                    getIndexUniqueName()+"_seg"+KiteUtils.int2str
                    (indexSegments.size(),4),null, stream);
            return segment;
        } catch (IOException e) {
            String errMsg = "Error: "+e.getMessage();
            KiteInstance.logError(errMsg);
            System.err.println(errMsg);
            return null;
        }
    }
}
