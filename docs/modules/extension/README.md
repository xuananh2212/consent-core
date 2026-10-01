# Extension Management module

Binds registered extension contracts by tenant, client, consent type, channel, capture method, evidence type and lifecycle point.

Supports ordering, timeout, retry, criticality and execution logging. Customer logic belongs in `ConsentExtension` implementations, not in the aggregate.
