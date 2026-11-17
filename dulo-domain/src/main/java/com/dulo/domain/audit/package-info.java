/**
 * The audit history: every change to a record is stored as a complete {@link
 * com.dulo.domain.audit.ChangeEvent}, so replaying a record's events gives its state at any point.
 */
@NullMarked
package com.dulo.domain.audit;

import org.jspecify.annotations.NullMarked;
