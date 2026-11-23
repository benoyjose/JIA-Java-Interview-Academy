import Foundation

/// Retention policy for versioned release folders. The active release is always retained.
public enum JIAOfflineReleaseRetention {
    public static func releasesToKeep(versions: [Int], activeVersion: Int?, maximumPrevious: Int = 1) -> Set<Int> {
        let cap = max(0, maximumPrevious)
        let unique = Set(versions)
        guard let activeVersion, unique.contains(activeVersion) else {
            return Set(unique.sorted(by: >).prefix(cap + 1))
        }
        let rollbackVersions = unique.filter { $0 != activeVersion && $0 < activeVersion }
            .sorted(by: >).prefix(cap)
        return Set([activeVersion]).union(rollbackVersions)
    }

    public static func releasesToRemove(versions: [Int], activeVersion: Int?, maximumPrevious: Int = 1) -> Set<Int> {
        Set(versions).subtracting(releasesToKeep(versions: versions, activeVersion: activeVersion,
                                                maximumPrevious: maximumPrevious))
    }
}
