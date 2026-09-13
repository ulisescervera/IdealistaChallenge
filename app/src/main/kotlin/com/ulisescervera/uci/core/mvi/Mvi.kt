package com.ulisescervera.uci.core.mvi

/**
 * The presentation contract of UCI. Three marker interfaces, one base
 * ViewModel, and nothing else -- these live in `:app` and not in `:domain`
 * because they carry presentation types (positions, indices) that change with
 * the design, not with the business rules.
 */

/**
 * Everything the screen needs to render, in one immutable object.
 *
 * A single state object rather than several `LiveData`s means the UI can never
 * be caught in an impossible combination -- "loading and error at the same
 * time" is not representable if the state says so.
 */
interface UiState

/** A gesture the user can perform on a screen. */
interface UiIntent

/**
 * A one-shot side effect: navigate, show a snackbar, fire a share sheet.
 *
 * Kept out of [UiState] because effects must fire exactly once. If "show
 * snackbar" were a state field, a configuration change would re-emit it and the
 * user would see the same message again after every rotation.
 */
interface UiEffect
