# Admin bulk review operations
The reusable review component selects only pending visible task IDs, de-duplicates selection and limits each batch to 50. It calls the existing single-item decision callback sequentially and explicitly reports partial failures. The parent page remains responsible for reviewer authorization, audit trail and queue refresh.
