import Foundation

/// Retention policy for versioned release folders. The active release is always retained.
public enum JIAOfflineReleaseRetention {
    public static func releasesToKeep(versions: [Int], activeVersion: Int?, maximumPrevious: Int = 1) -> Set<Int> {
        let cap = max(0, maximumPrevious)
        let ordered = Array(Set(versions)).sorted(by: >)
        var keep = Set(ordered.prefix(cap + (activeVersion == nil ? 0 : 1)))
        if let activeVersion { keep.insert(activeVersion) }
        return keep
    }

    public static func releasesToRemove(versions: [Int], activeVersion: Int?, maximumPrevious: Int = 1) -> Set<Int> {
        Set(versions).subtracting(releasesToKeep(versions: versions, activeVersion: activeVersion,
                                                maximumPrevious: maximumPrevious))
    }
}
